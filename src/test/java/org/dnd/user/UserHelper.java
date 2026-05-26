package org.dnd.user;

public class UserHelper {
  public static UserEntity createValidatedUser(String name, String password, String email) {
    UserEntity user = createUser(name, password);
    user.setEmailVerified(true);
    user.setEmail(email);
    return user;
  }

  public static UserEntity createInvalidatedUser(String name, String password, String email) {
    UserEntity user = createUser(name, password);
    user.setEmailVerified(false);
    user.setEmail(email);
    return user;
  }


  private static UserEntity createUser(String name, String password) {
    UserEntity user = new UserEntity();
    user.setName(name);
    user.setPassword(password);
    return user;
  }
}
