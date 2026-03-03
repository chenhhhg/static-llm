package bupt.staticllm.core.evaluation.impl;

import bupt.staticllm.common.model.UnifiedIssue;
import bupt.staticllm.core.evaluation.EvaluationStrategy;
import bupt.staticllm.core.evaluation.model.BenchmarkCase;
import bupt.staticllm.core.evaluation.model.EvaluationResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OwaspBenchmarkStrategy implements EvaluationStrategy {

    public static final Map<String, String> CATEGORY_MAPPING = new HashMap<>();

    static {
        // SpotBugs types to OWASP categories
        
        // SQL Injection -> CSV: sqli
        CATEGORY_MAPPING.put("SQL_INJECTION", "sqli");
        CATEGORY_MAPPING.put("SQL_INJECTION_PREPARED_STATEMENT", "sqli");
        CATEGORY_MAPPING.put("SQL_INJECTION_TURBINE", "sqli");
        CATEGORY_MAPPING.put("SQL_INJECTION_HIBERNATE", "sqli");
        CATEGORY_MAPPING.put("SQL_INJECTION_JDO", "sqli");
        CATEGORY_MAPPING.put("SQL_INJECTION_JPA", "sqli");
        CATEGORY_MAPPING.put("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE", "sqli");
        CATEGORY_MAPPING.put("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING", "sqli");

        // Command Injection
        CATEGORY_MAPPING.put("COMMAND_INJECTION", "cmdi");
        CATEGORY_MAPPING.put("OS_COMMAND_INJECTION", "cmdi");

        // Path Traversal -> CSV: pathtraver
        CATEGORY_MAPPING.put("PATH_TRAVERSAL_IN", "pathtraver");
        CATEGORY_MAPPING.put("PATH_TRAVERSAL_OUT", "pathtraver");

        // LDAP Injection -> CSV: ldapi
        CATEGORY_MAPPING.put("LDAP_INJECTION", "ldapi");

        // XPath Injection -> CSV: xpathi
        CATEGORY_MAPPING.put("XPATH_INJECTION", "xpathi");

        // XSS
        CATEGORY_MAPPING.put("XSS_REQUEST_PARAMETER_TO_SERVLET_WRITER", "xss");
        CATEGORY_MAPPING.put("XSS_REQUEST_PARAMETER_TO_SEND_ERROR", "xss");
        CATEGORY_MAPPING.put("XSS_SERVLET", "xss");
        CATEGORY_MAPPING.put("XSS_JSP_PRINT", "xss");
        CATEGORY_MAPPING.put("XSS_ATTRIBUTE", "xss");

        // Weak Cryptography
        CATEGORY_MAPPING.put("CIPHER_INTEGRITY", "crypto");
        CATEGORY_MAPPING.put("DES_USAGE", "crypto");
        CATEGORY_MAPPING.put("ECB_MODE", "crypto");
        CATEGORY_MAPPING.put("STATIC_IV", "crypto");
        CATEGORY_MAPPING.put("UNENCRYPTED_SOCKET", "crypto");
        CATEGORY_MAPPING.put("RSA_NO_PADDING", "crypto");
        CATEGORY_MAPPING.put("RSA_KEY_SIZE", "crypto");
        CATEGORY_MAPPING.put("PADDING_ORACLE", "crypto");

        // Weak Hashing
        // Note: OWASP Benchmark v1.2 distinguishes 'hash' from 'crypto'.
        CATEGORY_MAPPING.put("WEAK_MESSAGE_DIGEST_MD5", "hash");
        CATEGORY_MAPPING.put("WEAK_MESSAGE_DIGEST_SHA1", "hash");
        CATEGORY_MAPPING.put("WEAK_MESSAGE_DIGEST_MD2", "hash");
        CATEGORY_MAPPING.put("WEAK_MESSAGE_DIGEST_MD4", "hash");

        // Weak Random -> CSV: weakrand
        CATEGORY_MAPPING.put("PREDICTABLE_RANDOM", "weakrand");
        CATEGORY_MAPPING.put("DMI_RANDOM_USED_ONLY_ONCE", "weakrand");

        // Secure Cookie -> CSV: securecookie
        CATEGORY_MAPPING.put("HTTPONLY_COOKIE", "securecookie");
        CATEGORY_MAPPING.put("SECURE_COOKIE", "securecookie");
        CATEGORY_MAPPING.put("INSECURE_COOKIE", "securecookie");

        // Trust Boundary -> CSV: trustbound
        CATEGORY_MAPPING.put("TRUST_BOUNDARY_VIOLATION", "trustbound");
        
        // Add more mappings as needed
    }

    /**
     * 判断给定的 ruleId 是否能映射到 Benchmark 类别
     */
    public static boolean isMappedCategory(String ruleId) {
        return CATEGORY_MAPPING.containsKey(ruleId);
    }

    @Override
    public EvaluationResult evaluate(BenchmarkCase expected, List<UnifiedIssue> actualList) {
        EvaluationResult result = new EvaluationResult();
        result.setBenchmarkCase(expected);

        // Find matching issue in actual list
        UnifiedIssue matchedIssue = actualList.stream()
                .filter(issue -> isMatch(expected, issue))
                .findFirst()
                .orElse(null);

        if (expected.isRealVulnerability()) {
            if (matchedIssue != null) {
                result.setMatchStatus(EvaluationResult.MatchStatus.TP);
                result.setActualIssueId(matchedIssue.getRuleId()); // Use ruleId as ID
                result.setDetails("Detected correctly: " + matchedIssue.getRuleId());
            } else {
                result.setMatchStatus(EvaluationResult.MatchStatus.FN);
                result.setDetails("Missed vulnerability");
            }
        } else {
            if (matchedIssue != null) {
                result.setMatchStatus(EvaluationResult.MatchStatus.FP);
                result.setActualIssueId(matchedIssue.getRuleId());
                result.setDetails("False alarm: " + matchedIssue.getRuleId());
            } else {
                result.setMatchStatus(EvaluationResult.MatchStatus.TN);
                result.setDetails("Correctly ignored");
            }
        }

        return result;
    }

    private boolean isMatch(BenchmarkCase expected, UnifiedIssue actual) {
        // 1. File name match
        // Benchmark filename usually is class name like BenchmarkTest00001
        // Actual filename might be full path or just name.
        if (actual.getFilePath() == null || !actual.getFilePath().contains(expected.getFilename())) {
            return false;
        }

        // 2. Category match
        String normalizedActualCategory = normalizeCategory(actual.getRuleId());
        return normalizedActualCategory.equalsIgnoreCase(expected.getCategory());
    }

    @Override
    public String normalizeCategory(String rawCategory) {
        return CATEGORY_MAPPING.getOrDefault(rawCategory, rawCategory);
    }

    @Override
    public String getType() {
        return "OWASP-1.2";
    }
}
