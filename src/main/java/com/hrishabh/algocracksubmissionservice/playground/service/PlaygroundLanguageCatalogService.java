package com.hrishabh.algocracksubmissionservice.playground.service;

import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.LanguageDescriptorResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlaygroundLanguageCatalogService {

    private static final String JAVA_STARTER = """
            import java.util.*;

            public class Main {
                public static void main(String[] args) {
                    Scanner scanner = new Scanner(System.in);
                    // Write your code here.
                }
            }
            """;

    private static final String PYTHON_STARTER = """
            import sys

            def main():
                data = sys.stdin.read()
                # Write your code here.

            if __name__ == "__main__":
                main()
            """;

    public List<LanguageDescriptorResponse> listLanguages() {
        return List.of(
                new LanguageDescriptorResponse("JAVA", "Java", "java", JAVA_STARTER),
                new LanguageDescriptorResponse("PYTHON", "Python", "python", PYTHON_STARTER));
    }
}
