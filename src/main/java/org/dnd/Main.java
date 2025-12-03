package org.dnd;

public class Main {
    static final String ytLink = "https://www.youtube.com/watch?v=KQxXUGb78UY&list=RDKQxXUGb78UY";

    public static void main(String[] args) throws Exception {
        new AudioPlayerCore().playTrack(ytLink);
    }
}