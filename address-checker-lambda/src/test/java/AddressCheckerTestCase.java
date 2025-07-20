
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.Before;
import org.junit.Test;
import org.nz.postal.address.NZPostAddressCheckerLambda;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class AddressCheckerTestCase {

    private NZPostAddressCheckerLambda handler;
    private Context context;

    @Before
    public void setUp() {
        handler = new NZPostAddressCheckerLambda();

        // Mock Context
        context = new Context() {
            @Override public String getAwsRequestId() { return "test-request-id"; }
            @Override public String getLogGroupName() { return "test-log-group"; }
            @Override public String getLogStreamName() { return "test-log-stream"; }
            @Override public String getFunctionName() { return "NZPostAddressCheckerLambda"; }
            @Override public String getFunctionVersion() { return "1.0"; }
            @Override public String getInvokedFunctionArn() { return "arn:aws:lambda:local:test"; }
            @Override public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() { return null; }
            @Override public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() { return null; }
            @Override public int getRemainingTimeInMillis() { return 300000; }
            @Override public int getMemoryLimitInMB() { return 512; }

           /* @Override
            public LambdaLogger getLogger() {
                return null;
            }*/
            @Override public com.amazonaws.services.lambda.runtime.LambdaLogger getLogger() { return null;}
        };
    }

    @Test
    public void testHandleRequestWithValidParams() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("q", "Triangle");
        queryParams.put("max", "5");
        request.setQueryStringParameters(queryParams);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);


        assertNotNull("Response should not be null", response);
        assertEquals("Expected HTTP 200 status", 200, (int) response.getStatusCode());
        assertNotNull("Response body should not be null", response.getBody());
    }

    @Test
    public void testHandleRequestMissingQueryParam() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent(); // No query params

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertNotNull("Response should not be null", response);
        assertEquals("Expected HTTP 400 status", 400, (int) response.getStatusCode());
        assertTrue("Expected error message about missing 'q'",
                response.getBody().contains("Missing 'q' query parameter"));
    }
}
