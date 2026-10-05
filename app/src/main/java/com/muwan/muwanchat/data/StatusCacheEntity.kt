package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Status tab ka offline-first cache. ChannelsCacheEntity jaisa hi pattern:
// key -> JSON string, DB already per-uid hai. Keys:
//   "feed"          -> last server feed (mine + friends)
//   "pending_views" -> offline me dekhe gaye status ids (online aate hi sync)
//   "pending_likes" -> offline me like/unlike (online aate hi sync)
// JSON isliye ki status fields badalne par (likes, link title...) baar-baar
// migration na likhni pade.
@Entity(tableName = "status_cache")
data class StatusCacheEntity(
    @PrimaryKey val key: String,
    val json: String
)
