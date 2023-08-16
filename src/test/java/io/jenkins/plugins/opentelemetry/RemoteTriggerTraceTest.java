package io.jenkins.plugins.opentelemetry;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import jenkins.plugins.http_request.HttpRequest;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;
import static jenkins.plugins.http_request.HttpMode.POST;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;

public class RemoteTriggerTraceTest extends BaseIntegrationTest {

    @Test
    public void testRemoteTriggerParentChildTrace() throws Exception {

        //source->(remote call)->target->(local build)->target-sub

        jenkinsRule.jenkins.setAuthorizationStrategy(AuthorizationStrategy.Unsecured.UNSECURED);
        jenkinsRule.jenkins.setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
        jenkinsRule.jenkins.setCrumbIssuer(null);


        //First we create a sub-project
        FreeStyleProject targetSubProject = jenkinsRule.createFreeStyleProject("target-sub-project");

        //We create a pipeline job to build sub-project locally
        WorkflowJob targetProject = jenkinsRule.createProject(WorkflowJob.class, "target-project");
        String pipelineScript = "build job: '" + targetSubProject.getName() + "', wait: true";
        targetProject.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        //We create the source project remote triggers target with traceparent header
        FreeStyleProject sourceProject = jenkinsRule.createFreeStyleProject("source-project");
        String remoteUrl = jenkinsRule.getURL().toString();
        String targetProjectUrl = targetProject.getUrl();
        HttpRequest httpRequest = new HttpRequest(remoteUrl + targetProjectUrl + "build");
        httpRequest.setHttpMode(POST);
        httpRequest.setCustomHeaders(List.of(new HttpRequestNameValuePair("traceparent", "${TRACEPARENT}")));
        sourceProject.getBuildersList().add(httpRequest);

        jenkinsRule.buildAndAssertSuccess(sourceProject);

        await().atMost(30, SECONDS).untilAsserted(() ->
            {
                Run sourceBuild = sourceProject.getLastBuild();
                assertThat(sourceProject.getName() + " should complete successfully",
                    sourceBuild != null && Result.SUCCESS.equals(sourceBuild.getResult()));

                Run targetBuild = targetProject.getLastBuild();
                assertThat(targetProject.getName() + " should complete successfully",
                    targetBuild != null && Result.SUCCESS.equals(targetBuild.getResult()));

                Run targetSubBuild = targetSubProject.getLastBuild();
                assertThat(targetSubProject.getName() + " should complete successfully",
                    targetSubBuild != null && Result.SUCCESS.equals(targetSubBuild.getResult()));
            }

        );


        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + sourceProject.getName();

        //root span is the source project
        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        String targetSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + targetProject.getName();


        Optional<Tree.Node<SpanDataWrapper>> targetJobSpan = spans.breadthFirstSearchNodes(node -> targetSpanName.equals(node.getData().spanData.getName()));
        assertThat("Should have target job span in the tree", targetJobSpan.isPresent());


        String targetSubSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + targetSubProject.getName();

        Optional<Tree.Node<SpanDataWrapper>> targetSubSpan = targetJobSpan.get().asTree()
            .breadthFirstSearchNodes(node -> targetSubSpanName.equals(node.getData().spanData.getName()));
        assertThat("Should have target sub job span in the tree", targetSubSpan.isPresent());


    }

}
