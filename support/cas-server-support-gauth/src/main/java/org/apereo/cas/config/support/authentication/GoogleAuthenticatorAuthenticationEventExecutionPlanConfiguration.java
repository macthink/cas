package org.apereo.cas.config.support.authentication;

import org.apereo.cas.CipherExecutor;
import org.apereo.cas.adaptors.gauth.GoogleAuthenticatorAuthenticationHandler;
import org.apereo.cas.adaptors.gauth.GoogleAuthenticatorMultifactorAuthenticationProvider;
import org.apereo.cas.adaptors.gauth.GoogleAuthenticatorTokenCredential;
import org.apereo.cas.adaptors.gauth.repository.credentials.InMemoryGoogleAuthenticatorTokenCredentialRepository;
import org.apereo.cas.adaptors.gauth.repository.credentials.JsonGoogleAuthenticatorTokenCredentialRepository;
import org.apereo.cas.adaptors.gauth.repository.credentials.RestGoogleAuthenticatorTokenCredentialRepository;
import org.apereo.cas.authentication.AuthenticationEventExecutionPlanConfigurer;
import org.apereo.cas.authentication.AuthenticationHandler;
import org.apereo.cas.authentication.AuthenticationMetaDataPopulator;
import org.apereo.cas.authentication.ByCredentialTypeAuthenticationHandlerResolver;
import org.apereo.cas.authentication.MultifactorAuthenticationProviderBypass;
import org.apereo.cas.authentication.MultifactorAuthenticationUtils;
import org.apereo.cas.authentication.metadata.AuthenticationContextAttributeMetaDataPopulator;
import org.apereo.cas.authentication.principal.PrincipalFactory;
import org.apereo.cas.authentication.principal.PrincipalFactoryUtils;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.otp.repository.credentials.OneTimeTokenAccountCipherExecutor;
import org.apereo.cas.otp.repository.credentials.OneTimeTokenCredentialRepository;
import org.apereo.cas.otp.repository.token.OneTimeTokenRepository;
import org.apereo.cas.otp.repository.token.OneTimeTokenRepositoryCleaner;
import org.apereo.cas.otp.web.flow.OneTimeTokenAccountCheckRegistrationAction;
import org.apereo.cas.otp.web.flow.OneTimeTokenAccountSaveRegistrationAction;
import org.apereo.cas.services.MultifactorAuthenticationProvider;
import org.apereo.cas.services.ServicesManager;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.IGoogleAuthenticator;
import com.warrenstrange.googleauth.KeyRepresentation;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.webflow.execution.Action;

import java.util.concurrent.TimeUnit;

/**
 * This is {@link GoogleAuthenticatorAuthenticationEventExecutionPlanConfiguration}.
 *
 * @author Misagh Moayyed
 * @author Dmitriy Kopylenko
 * @since 5.1.0
 */
@Configuration("googleAuthenticatorAuthenticationEventExecutionPlanConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Slf4j
public class GoogleAuthenticatorAuthenticationEventExecutionPlanConfiguration {

    @Lazy
    @Autowired
    @Qualifier("googleAuthenticatorAccountRegistry")
    private OneTimeTokenCredentialRepository googleAuthenticatorAccountRegistry;

    @Lazy
    @Autowired
    @Qualifier("oneTimeTokenAuthenticatorTokenRepository")
    private OneTimeTokenRepository oneTimeTokenAuthenticatorTokenRepository;

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("servicesManager")
    private ServicesManager servicesManager;

    @Bean
    public IGoogleAuthenticator googleAuthenticatorInstance() {
        val gauth = casProperties.getAuthn().getMfa().getGauth();
        val bldr = new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder();

        bldr.setCodeDigits(gauth.getCodeDigits());
        bldr.setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(gauth.getTimeStepSize()));
        bldr.setWindowSize(gauth.getWindowSize());
        bldr.setKeyRepresentation(KeyRepresentation.BASE32);
        return new GoogleAuthenticator(bldr.build());
    }

    @ConditionalOnMissingBean(name = "googleAuthenticatorAuthenticationHandler")
    @Bean
    @RefreshScope
    public AuthenticationHandler googleAuthenticatorAuthenticationHandler() {
        return new GoogleAuthenticatorAuthenticationHandler(casProperties.getAuthn().getMfa().getGauth().getName(), servicesManager, googlePrincipalFactory(),
            googleAuthenticatorInstance(), oneTimeTokenAuthenticatorTokenRepository, googleAuthenticatorAccountRegistry);
    }

    @Bean
    @RefreshScope
    public MultifactorAuthenticationProviderBypass googleBypassEvaluator() {
        return MultifactorAuthenticationUtils.newMultifactorAuthenticationProviderBypass(casProperties.getAuthn().getMfa().getGauth().getBypass());
    }

    @Bean
    @RefreshScope
    public MultifactorAuthenticationProvider googleAuthenticatorAuthenticationProvider() {
        val gauth = casProperties.getAuthn().getMfa().getGauth();
        val p = new GoogleAuthenticatorMultifactorAuthenticationProvider();
        p.setBypassEvaluator(googleBypassEvaluator());
        p.setGlobalFailureMode(casProperties.getAuthn().getMfa().getGlobalFailureMode());
        p.setOrder(gauth.getRank());
        p.setId(gauth.getId());
        return p;
    }

    @Bean
    @RefreshScope
    public AuthenticationMetaDataPopulator googleAuthenticatorAuthenticationMetaDataPopulator() {
        return new AuthenticationContextAttributeMetaDataPopulator(
            casProperties.getAuthn().getMfa().getAuthenticationContextAttribute(),
            googleAuthenticatorAuthenticationHandler(),
            googleAuthenticatorAuthenticationProvider()
        );
    }

    @Bean
    @RefreshScope
    public Action googleAccountRegistrationAction() {
        val gauth = casProperties.getAuthn().getMfa().getGauth();
        return new OneTimeTokenAccountCheckRegistrationAction(googleAuthenticatorAccountRegistry,
            gauth.getLabel(),
            gauth.getIssuer());
    }

    @ConditionalOnProperty(prefix = "cas.authn.mfa.gauth.cleaner", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    @Autowired
    public OneTimeTokenRepositoryCleaner googleAuthenticatorTokenRepositoryCleaner(@Qualifier("oneTimeTokenAuthenticatorTokenRepository")
                                                                                   final OneTimeTokenRepository oneTimeTokenAuthenticatorTokenRepository) {
        return new GoogleAuthenticatorOneTimeTokenRepositoryCleaner(oneTimeTokenAuthenticatorTokenRepository);
    }

    @ConditionalOnMissingBean(name = "googleAuthenticatorAccountRegistry")
    @Bean
    @RefreshScope
    public OneTimeTokenCredentialRepository googleAuthenticatorAccountRegistry() {
        val gauth = casProperties.getAuthn().getMfa().getGauth();
        if (gauth.getJson().getLocation() != null) {
            return new JsonGoogleAuthenticatorTokenCredentialRepository(gauth.getJson().getLocation(), googleAuthenticatorInstance(),
                googleAuthenticatorAccountCipherExecutor());
        }
        if (StringUtils.isNotBlank(gauth.getRest().getEndpointUrl())) {
            return new RestGoogleAuthenticatorTokenCredentialRepository(googleAuthenticatorInstance(),
                new RestTemplate(), gauth, googleAuthenticatorAccountCipherExecutor());
        }
        return new InMemoryGoogleAuthenticatorTokenCredentialRepository(googleAuthenticatorAccountCipherExecutor(), googleAuthenticatorInstance());
    }

    @ConditionalOnMissingBean(name = "googleAuthenticatorAccountCipherExecutor")
    @Bean
    @RefreshScope
    public CipherExecutor googleAuthenticatorAccountCipherExecutor() {
        val crypto = casProperties.getAuthn().getMfa().getGauth().getCrypto();
        if (crypto.isEnabled()) {
            return new OneTimeTokenAccountCipherExecutor(
                crypto.getEncryption().getKey(),
                crypto.getSigning().getKey(),
                crypto.getAlg(),
                "Google Authenticator Token Account");
        }
        LOGGER.warn("Google Authenticator one-time token account encryption/signing is turned off. "
            + "Consider turning on encryption, signing to securely and safely store one-time token accounts.");
        return CipherExecutor.noOp();
    }

    @Bean
    @RefreshScope
    public Action googleSaveAccountRegistrationAction() {
        return new OneTimeTokenAccountSaveRegistrationAction(this.googleAuthenticatorAccountRegistry);
    }

    @ConditionalOnMissingBean(name = "googlePrincipalFactory")
    @Bean
    public PrincipalFactory googlePrincipalFactory() {
        return PrincipalFactoryUtils.newPrincipalFactory();
    }

    @ConditionalOnMissingBean(name = "googleAuthenticatorAuthenticationEventExecutionPlanConfigurer")
    @Bean
    public AuthenticationEventExecutionPlanConfigurer googleAuthenticatorAuthenticationEventExecutionPlanConfigurer() {
        return plan -> {
            if (StringUtils.isNotBlank(casProperties.getAuthn().getMfa().getGauth().getIssuer())) {
                plan.registerAuthenticationHandler(googleAuthenticatorAuthenticationHandler());
                plan.registerMetadataPopulator(googleAuthenticatorAuthenticationMetaDataPopulator());
                plan.registerAuthenticationHandlerResolver(new ByCredentialTypeAuthenticationHandlerResolver(GoogleAuthenticatorTokenCredential.class));
            }
        };
    }

    /**
     * The type Google authenticator one time token repository cleaner.
     */
    public static class GoogleAuthenticatorOneTimeTokenRepositoryCleaner extends OneTimeTokenRepositoryCleaner {
        public GoogleAuthenticatorOneTimeTokenRepositoryCleaner(final OneTimeTokenRepository tokenRepository) {
            super(tokenRepository);
        }

        @Scheduled(initialDelayString = "${cas.authn.mfa.gauth.cleaner.schedule.startDelay:PT30S}",
            fixedDelayString = "${cas.authn.mfa.gauth.cleaner.schedule.repeatInterval:PT35S}")
        @Override
        public void clean() {
            super.clean();
        }
    }
}
