package org.dnd.configuration.limiting;

public final class RateLimitNames {

  public static final String DEFAULT_API = "default-api";
  public static final String CREATE_API = "create-api";

  public static final String LOGIN_ACCOUNT = "login-account";
  public static final String REGISTER_SUBJECT = "register-subject";
  public static final String VERIFY_EMAIL_TOKEN = "verify-email-token";
  public static final String RESEND_VERIFICATION_SUBJECT = "resend-verification-subject";
  public static final String FORGOT_PASSWORD_EMAIL = "forgot-password-email";
  public static final String PASSWORD_TOKEN = "password-token";
  public static final String CHANGE_PASSWORD_ACCOUNT = "change-password-account";

  public static final String STREAM_API = "stream-api";

  public static final String CURRENT_USER_KEY =
          "@rateLimitKeyResolver.currentUserKey()";

  public static final String LOGIN_ACCOUNT_KEY =
          "@rateLimitKeyResolver.loginAccountKey(#userLoginRequest)";

  public static final String REGISTER_SUBJECT_KEY =
          "@rateLimitKeyResolver.registerSubjectKey(#userRegisterRequest)";

  public static final String VERIFY_EMAIL_TOKEN_KEY =
          "@rateLimitKeyResolver.verifyEmailTokenKey(#verificationToken)";

  public static final String RESEND_VERIFICATION_LOGIN_KEY =
          "@rateLimitKeyResolver.resendVerificationKey(#request)";

  public static final String RESEND_VERIFICATION_REGISTER_KEY =
          "@rateLimitKeyResolver.resendVerificationKey(#userAuthDTO)";

  public static final String FORGOT_PASSWORD_EMAIL_KEY =
          "@rateLimitKeyResolver.forgotPasswordEmailKey(#forgotPasswordRequest)";

  public static final String PASSWORD_TOKEN_KEY =
          "@rateLimitKeyResolver.changePasswordTokenKey(#userChangePasswordWithTokenRequest)";

  public static final String CHANGE_PASSWORD_ACCOUNT_KEY =
          "@rateLimitKeyResolver.changePasswordAccountKey(#userChangePasswordRequest)";

  public static final String STREAM_TOKEN_KEY =
          "@rateLimitKeyResolver.streamKey(#streamToken)";

  private RateLimitNames() {
  }
}