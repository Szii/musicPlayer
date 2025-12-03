package org.dnd;

public class Program {
    static final String ytLink = "https://www.youtube.com/watch?v=KQxXUGb78UY&list=RDKQxXUGb78UY";

    public void run() {
        AudioPlayerCore core = new AudioPlayerCore();
        try {
            core.loadTrack(ytLink);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
