package com.meridian.ai;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputItem;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * {@link ConsensusSummaryEngine}의 실제 구현체. {@link OpenAiCultureAnalysisEngine}과 같은 방식으로
 * OpenAI Responses API Structured Outputs를 호출한다.
 */
@Component
public class OpenAiConsensusSummaryEngine implements ConsensusSummaryEngine {

    private final OpenAiProperties properties;
    private final OpenAIClient client;

    public OpenAiConsensusSummaryEngine(OpenAiProperties properties, @Lazy OpenAIClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public ConsensusAnalysisResult analyze(String proposalTitle, String proposalContent, List<ConsensusOpinionInput> opinions, String responseLanguage) {
        String model = requireConfig(properties.model(), "OPENAI_MODEL");

        StructuredResponseCreateParams<ConsensusAnalysisResult> params =
                StructuredResponseCreateParams.<ConsensusAnalysisResult>builder()
                        .input(prompt(proposalTitle, proposalContent, opinions, responseLanguage))
                        .model(model)
                        .text(ConsensusAnalysisResult.class)
                        .build();

        StructuredResponse<ConsensusAnalysisResult> response = client.responses().create(params);

        return response.output().stream()
                .filter(StructuredResponseOutputItem::isMessage)
                .map(StructuredResponseOutputItem::asMessage)
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OpenAI response did not contain structured output text."));
    }

    private String prompt(String proposalTitle, String proposalContent, List<ConsensusOpinionInput> opinions, String responseLanguage) {
        String opinionLines = IntStream.range(0, opinions.size())
                .mapToObj(index -> {
                    ConsensusOpinionInput opinion = opinions.get(index);
                    return "%d. [%s] %s".formatted(index + 1, opinion.stance(), opinion.comment());
                })
                .collect(Collectors.joining("\n"));

        return """
                A team proposal has collected asynchronous opinions from global team members who may \
                express disagreement indirectly or politely due to cultural communication style. \
                Analyze the overall consensus.

                Proposal title:
                %s

                Proposal content:
                %s

                Opinions (stance in brackets, AGREE / DISAGREE / CONDITIONAL_AGREE):
                %s

                Return:
                - consensusStatus: AGREED if broad support, PARTIAL if mixed with conditions, \
                DISAGREED if opposition dominates, PENDING if inconclusive (keep this value in English \
                — it is a fixed enum)
                - summary: a short summary, written in %s
                - keyIssues: a list, written in %s
                - culturalAnalysis: notes on how the opinions might be interpreted differently across \
                cultures, written in %s
                - hiddenOpposition: politely-worded concerns that may actually be disagreement, written in %s
                - recommendedActions: what the team should do next, written in %s

                Do not include any part of this prompt in your output.
                """.formatted(proposalTitle, proposalContent, opinionLines,
                        responseLanguage, responseLanguage, responseLanguage, responseLanguage, responseLanguage);
    }

    private String requireConfig(String value, String envVarName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(envVarName + " is not configured.");
        }
        return value;
    }
}
