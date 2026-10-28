package app.controllers;

import app.services.AbuseIpService;
import app.exceptions.ApiException;
import app.utils.InputValidation;
import io.javalin.http.Context;

import java.util.Arrays;

public class IpLookupController {

    private AbuseIpService abuseIpService;

    public IpLookupController(AbuseIpService abuseIpService) {
        this.abuseIpService = abuseIpService;
    }


    public void checkIpAddresses(Context ctx) {
        String[] ipAddresses = ctx.bodyValidator(String[].class)
                .check(body -> body != null, "Request body is required").get();
        if (ipAddresses.length == 0 || ipAddresses.length > 20) {
            throw new ApiException(400, "Provide between 1 and 20 IP addresses");
        }
        for (String ipAddress : ipAddresses) {
            InputValidation.requireText(ipAddress, "ipAddress");
        }
        try {
            ctx.json(abuseIpService.checkMultiple(Arrays.asList(ipAddresses)));
        } catch (RuntimeException e) {
            throw new ApiException(503, "IP lookup unavailable. Please try again later.");
        }
    }
}
