package org.dnd.user.rank;

public record UserRankLimits(
        Integer maxSessions,
        Integer maxTracks,
        Integer maxWindows,
        Integer maxBoards,
        Integer maxGroups,
        Integer maxShares
) {

  public static UserRankLimits normal() {
    return new UserRankLimits(
            3,
            30,
            3,
            5,
            5,
            10
    );
  }

  public static UserRankLimits unrestricted() {
    return new UserRankLimits(
            null,
            null,
            null,
            null,
            null,
            null
    );
  }

  public boolean canCreate(Integer max, int actual) {
    return max == null || actual < max;
  }

  public boolean isLimitReached(Integer max, int actual) {
    return max != null && actual >= max;
  }
}
