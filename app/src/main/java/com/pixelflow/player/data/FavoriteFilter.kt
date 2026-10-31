package com.pixelflow.player.data

enum class FavoriteFilter(val label: String, val description: String) {
    ALL("All Videos", "Play all videos in this album"),
    FAVORITES_ONLY("Favorites Only", "Play only videos marked as favorites in Photos"),
    NON_FAVORITES_ONLY("Non-Favorites Only", "Play only non-favorited videos"),
    NON_FAVORITES_UNVIEWED("Unviewed Non-Favorites", "Play only non-favorited videos you haven't watched yet")
}
