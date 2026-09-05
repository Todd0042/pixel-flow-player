package com.pixelflow.player.data

enum class PlayOrder(val label: String, val description: String) {
    NEWEST("Newest First", "Most recently added/recorded videos first"),
    OLDEST("Oldest First", "Chronological order, starting from oldest"),
    LONGEST("Longest First", "Longest duration videos first"),
    RANDOM("Random", "Shuffled in a random play order")
}
