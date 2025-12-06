package org.dnd;

import org.springframework.stereotype.Component;

@Component
public class Program {
    static final String ytLink = "https://www.youtube.com/watch?v=FrWuCPgsp_c&list=RDFrWuCPgsp_c&start_radio=1";

    public void run() {
        AudioPlayerCore core = new AudioPlayerCore();
        try {
            core.loadTrack(ytLink);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
