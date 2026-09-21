package ru.vyatsu.ita.lab09.analysis;

import ru.vyatsu.ita.lab09.domain.AnalysisRequest;
import ru.vyatsu.ita.lab09.domain.SourceMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SimpleJavaAnalyzer implements SourceAnalyzer {
    private static final Pattern METHOD = Pattern.compile(
            "(?:public|protected|private)\\s+(?:static\\s+)?[\\w<>\\[\\]]+\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{");

    @Override
    public List<SourceMethod> analyze(AnalysisRequest request) {
        List<SourceMethod> result = new ArrayList<>();
        Matcher matcher = METHOD.matcher(request.sourceCode());
        while (matcher.find()) {
            String params = matcher.group(2).trim();
            int parameterCount = params.isEmpty() ? 0 : params.split(",").length;
            int bodyEnd = Math.min(request.sourceCode().length(), matcher.end() + 500);
            String body = request.sourceCode().substring(matcher.end(), bodyEnd);
            int complexity = 1 + count(body, "if\\s*\\(") + count(body, "for\\s*\\(")
                    + count(body, "while\\s*\\(") + count(body, "case\\s+");
            result.add(new SourceMethod(matcher.group(1), parameterCount, complexity));
        }
        return List.copyOf(result);
    }

    private int count(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
}
