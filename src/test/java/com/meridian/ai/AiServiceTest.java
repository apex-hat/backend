package com.meridian.ai;

import com.meridian.auth.AuthenticationException;
import com.meridian.user.User;
import com.meridian.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    private static final String AUTH_HEADER = "Bearer id-token";

    @Mock
    private UserService userService;
    @Mock
    private CultureAnalysisEngine cultureAnalysisEngine;
    @Mock
    private CultureAnalysisRepository cultureAnalysisRepository;
    @Mock
    private IntentAnalysisEngine intentAnalysisEngine;

    private AiService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AiService(userService, cultureAnalysisEngine, cultureAnalysisRepository, intentAnalysisEngine);
        lenient().when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(User.builder().id(1L).build());
    }

    @Test
    void persistsAndReturnsEngineResult() {
        ContextAnalysisRequest request = new ContextAnalysisRequest(
                "괜찮은 것 같아요. 그런데...", List.of("KR", "US"));

        CultureAnalysisResult engineResult = new CultureAnalysisResult(
                RiskLevel.HIGH,
                List.of(new CultureInterpretation("KR", "완곡한 반대 또는 우려로 해석될 가능성이 있음"),
                        new CultureInterpretation("US", "기본적으로 긍정적인 의견으로 해석될 가능성이 있음")),
                List.of("괜찮은 것 같아요"),
                "전체적으로 긍정적이지만 일정 부분 수정이 필요하다고 생각합니다.");
        when(cultureAnalysisEngine.analyze("괜찮은 것 같아요. 그런데...", List.of("KR", "US"))).thenReturn(engineResult);
        when(cultureAnalysisRepository.save(any(CultureAnalysis.class))).thenAnswer(invocation -> {
            CultureAnalysis analysis = invocation.getArgument(0);
            analysis.setId(1L);
            return analysis;
        });

        ContextAnalysisResponse response = aiService.contextAnalysis(AUTH_HEADER, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.originalText()).isEqualTo("괜찮은 것 같아요. 그런데...");
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.interpretations()).containsExactly(
                new CultureInterpretation("KR", "완곡한 반대 또는 우려로 해석될 가능성이 있음"),
                new CultureInterpretation("US", "기본적으로 긍정적인 의견으로 해석될 가능성이 있음"));
        assertThat(response.flaggedPhrases()).containsExactly("괜찮은 것 같아요");
        assertThat(response.suggestion()).isEqualTo("전체적으로 긍정적이지만 일정 부분 수정이 필요하다고 생각합니다.");
    }

    @Test
    void savesProposalIdAsNullForPreRegistrationAnalysis() {
        ContextAnalysisRequest request = new ContextAnalysisRequest("원문", List.of("KR"));
        when(cultureAnalysisEngine.analyze("원문", List.of("KR")))
                .thenReturn(new CultureAnalysisResult(RiskLevel.LOW, List.of(), List.of(), "원문"));
        when(cultureAnalysisRepository.save(any(CultureAnalysis.class))).thenAnswer(invocation -> {
            CultureAnalysis analysis = invocation.getArgument(0);
            assertThat(analysis.getProposal()).isNull();
            analysis.setId(1L);
            return analysis;
        });

        aiService.contextAnalysis(AUTH_HEADER, request);
    }

    @Test
    void rejectsMissingBearerToken() {
        when(userService.getCurrentUserEntity(null))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        ContextAnalysisRequest request = new ContextAnalysisRequest("원문", List.of());

        assertThatThrownBy(() -> aiService.contextAnalysis(null, request))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void intentAnalysisReturnsEngineResultWithoutPersisting() {
        IntentAnalysisRequest request = new IntentAnalysisRequest("괜찮은 것 같아요. 다만 일정이 조금 걱정되네요.");
        when(intentAnalysisEngine.analyze(request.content()))
                .thenReturn(new IntentAnalysisResult("긍정", "일정 측면에서 조건부 반대 또는 우려 가능성"));

        IntentAnalysisResponse response = aiService.intentAnalysis(AUTH_HEADER, request);

        assertThat(response.content()).isEqualTo(request.content());
        assertThat(response.surfaceOpinion()).isEqualTo("긍정");
        assertThat(response.potentialOpinion()).isEqualTo("일정 측면에서 조건부 반대 또는 우려 가능성");
    }

    @Test
    void intentAnalysisRejectsMissingBearerToken() {
        when(userService.getCurrentUserEntity(null))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        IntentAnalysisRequest request = new IntentAnalysisRequest("원문");

        assertThatThrownBy(() -> aiService.intentAnalysis(null, request))
                .isInstanceOf(AuthenticationException.class);
    }
}
