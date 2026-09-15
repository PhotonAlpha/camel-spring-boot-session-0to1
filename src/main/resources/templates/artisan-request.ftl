<#--
  ============================================================================
   artisan-request.ftl  -  outbound transformation for API-7
  ============================================================================
   Renders the internal order into the MyAccountDetailsVO shape the artisan
   service expects. The httpexchange: component renders this BEFORE the call and
   binds the result to the @RequestBody parameter, so the Java side never
   mentions the remote field names.

   Model variables are the same as for the freemarker: component
   (body / headers / exchange), so this file would render identically either way.
  ============================================================================
-->
{
  "userId":   "${body.orderId!"N/A"}",
  "userName": "${headers.userName!"anonymous"}",
  "channel":  "${body.channel!"WEB"}",
  "amount":   ${(body.amount!0)?c}
}
