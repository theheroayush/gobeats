package com.example.gobeats.core.playback

import com.example.gobeats.core.model.Track
import org.json.JSONArray

object ProcessDeathPersistenceEngine {
    fun serializeQueue(trackIds: List<String>): String {
        val jsonArray = JSONArray()
        trackIds.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    fun deserializeQueue(queueJson: String): List<String> {
        val array = JSONArray(queueJson)
        val list = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }

    fun sortTracksByQueueIds(tracks: List<Track>, queueIds: List<String>): List<Track> {
        val map = tracks.associateBy { it.id }
        return queueIds.mapNotNull { map[it] }
    }
}
