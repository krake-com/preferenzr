<#-- template to create the name of the runner script -->
<#if osName?upper_case?contains("WIN")>
preferenzr.cmd
<#else>
preferenzr.sh
</#if>