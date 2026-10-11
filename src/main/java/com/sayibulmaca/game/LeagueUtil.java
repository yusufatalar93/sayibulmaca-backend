package com.sayibulmaca.game;

public class LeagueUtil {

    public static String getLeague(int rating) {
        if (rating < 1000) return "BRONZE";
        if (rating < 1200) return "SILVER";
        if (rating < 1400) return "GOLD";
        if (rating < 1600) return "PLATINUM";
        if (rating < 1800) return "DIAMOND";
        if (rating < 2000) return "MASTER";
        return "LEGEND";
    }

    public static String getTier(int rating) {
        if (rating >= 2000) return null; // Legend has no tier
        
        int offset = rating % 200;
        if (rating < 1000) {
             offset = (rating - 800);
             if(offset < 0) offset = 0;
        }

        if (offset < 50) return "IV";
        if (offset < 100) return "III";
        if (offset < 150) return "II";
        return "I";
    }
    
    public static Integer getProgressToNextTier(int rating) {
        if (rating >= 2000) return null;
        int offset = rating % 200;
         if (rating < 1000) {
             offset = (rating - 800);
             if(offset < 0) offset = 0;
        }
        return offset % 50; // returns 0-49
    }
}
