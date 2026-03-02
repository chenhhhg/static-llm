package bupt.staticllm.core.evaluation.impl;

import bupt.staticllm.core.evaluation.EvaluationProvider;
import bupt.staticllm.core.evaluation.model.BenchmarkCase;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class OwaspBenchmarkProvider implements EvaluationProvider {

    @Override
    public List<BenchmarkCase> loadCases(String sourcePath) {
        List<BenchmarkCase> cases = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(sourcePath))) {
            String[] header = reader.readNext(); // Skip header
            // Assuming header: test name, category, real vulnerability, cwe, ...
            // Adjust index based on actual CSV structure. 
            // For OWASP Benchmark 1.2 expectedresults.csv:
            // 0: test name (e.g., BenchmarkTest00001)
            // 1: category (e.g., sql-injection)
            // 2: real vulnerability (true/false)
            // 3: cwe (e.g., 89)
            
            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length < 4) continue;
                
                BenchmarkCase benchmarkCase = new BenchmarkCase();
                benchmarkCase.setFilename(line[0]); // Usually class name or file name
                benchmarkCase.setCategory(line[1]);
                benchmarkCase.setRealVulnerability(Boolean.parseBoolean(line[2]));
                benchmarkCase.setCweId(line[3]);
                
                cases.add(benchmarkCase);
            }
        } catch (IOException | CsvValidationException e) {
            log.error("Failed to load OWASP Benchmark cases from {}", sourcePath, e);
            throw new RuntimeException("Failed to load benchmark cases", e);
        }
        return cases;
    }

    @Override
    public String getType() {
        return "OWASP-1.2";
    }
}
