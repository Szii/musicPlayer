package org.dnd.user.rank;

import org.dnd.user.UserRank;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class UserRankLimitProvider {

  private final Map<UserRank, UserRankLimits> limitsByRank = new EnumMap<>(UserRank.class);

  public UserRankLimitProvider() {
    limitsByRank.put(UserRank.NORMAL, UserRankLimits.normal());
    limitsByRank.put(UserRank.UNRESTRICTED, UserRankLimits.unrestricted());
  }

  public UserRankLimits getLimits(UserRank rank) {
    return limitsByRank.getOrDefault(rank, UserRankLimits.normal());
  }
}
