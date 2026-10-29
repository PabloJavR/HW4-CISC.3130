import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/*
 * Structure choices:
 *   Playlist    - BUILD: doubly linked list (SongNode) with first/last
 *   Library     - REUSE: HashMap "songBook"  (id -> playlist SongNode)
 *   Up next     - BUILD: QueueLine, a deque on a doubly linked list
 *   History     - BUILD: PlayedStack, a stack on a singly linked list
 *   Play counts - REUSE: HashMap "tally"     (id -> times played)
 */
public class MusicPlayer {

    // One spot in the playlist (also used inside the queue line)
    private static class SongNode {
        Song song;
        SongNode before = null;
        SongNode after = null;
        SongNode(Song song) { this.song = song; }
    }

    // Deque: add or take at both ends in O(1)
    private static class QueueLine implements Iterable<Song> {
        SongNode first = null;
        SongNode last = null;

        boolean isEmpty() { return first == null; }

        void addFront(Song song) {
            SongNode spot = new SongNode(song);
            if (first == null) {
                first = last = spot;
            } else {
                spot.after = first;
                first.before = spot;
                first = spot;
            }
        }

        void addBack(Song song) {
            SongNode spot = new SongNode(song);
            if (last == null) {
                first = last = spot;
            } else {
                spot.before = last;
                last.after = spot;
                last = spot;
            }
        }

        Song takeFront() {
            if (first == null) return null;
            SongNode spot = first;
            first = spot.after;
            if (first == null) last = null;
            else first.before = null;
            return spot.song;
        }

        // front -> back
        @Override
        public Iterator<Song> iterator() {
            return new Iterator<Song>() {
                SongNode s = first;
                public boolean hasNext() { return s != null; }
                public Song next() {
                    Song song = s.song;
                    s = s.after;
                    return song;
                }
            };
        }
    }

    // One layer of the history stack
    private static class PlayedNode {
        Song song;
        PlayedNode under;
        PlayedNode(Song song, PlayedNode under) {
            this.song = song;
            this.under = under;
        }
    }

    // Stack: the most recent song comes off first
    private static class PlayedStack implements Iterable<Song> {
        PlayedNode topNode = null;

        boolean isEmpty() { return topNode == null; }

        void push(Song song) { topNode = new PlayedNode(song, topNode); }

        Song pop() {
            if (topNode == null) return null;
            Song song = topNode.song;
            topNode = topNode.under;
            return song;
        }

        // most recent -> oldest
        @Override
        public Iterator<Song> iterator() {
            return new Iterator<Song>() {
                PlayedNode s = topNode;
                public boolean hasNext() { return s != null; }
                public Song next() {
                    Song song = s.song;
                    s = s.under;
                    return song;
                }
            };
        }
    }

    private SongNode first = null;                       // playlist start
    private SongNode last = null;                        // playlist end
    private final Map<String, SongNode> songBook = new HashMap<>();   // id -> SongNode
    private final QueueLine lineup = new QueueLine();    // up next
    private final PlayedStack played = new PlayedStack();// history
    private final Map<String, Integer> tally = new HashMap<>();       // id -> play count
    private Song current = null;                         // song playing now

    // Only way a song starts: sets current and adds 1 to its count
    private void begin(Song song) {
        current = song;
        tally.merge(song.id, 1, Integer::sum);
    }

    // ---------- Step 1: playlist and library ----------
    public boolean addSong(String id, String title, String artist) {
        if (songBook.containsKey(id)) return false;
        SongNode spot = new SongNode(new Song(id, title, artist));
        if (last == null) {
            first = last = spot;
        } else {
            spot.before = last;
            last.after = spot;
            last = spot;
        }
        songBook.put(id, spot);
        return true;
    }

    public Song findSong(String id) {
        SongNode spot = songBook.get(id);
        return spot == null ? null : spot.song;
    }

    public List<String> showPlaylist() {
        List<String> titles = new ArrayList<>();
        for (SongNode s = first; s != null; s = s.after) titles.add(s.song.title);
        return titles;
    }

    // ---------- Step 2: playing ----------
    public Song play(String id) {
        SongNode spot = songBook.get(id);
        if (spot == null) return null;
        if (current != null) played.push(current);
        begin(spot.song);
        return current;
    }

    public Song nowPlaying() {
        return current;
    }

    public int playCount(String id) {
        return tally.getOrDefault(id, 0);
    }

    // ---------- Step 3: up next ----------
    public boolean queueLater(String id) {
        SongNode spot = songBook.get(id);
        if (spot == null) return false;
        lineup.addBack(spot.song);
        return true;
    }

    public boolean queueNext(String id) {
        SongNode spot = songBook.get(id);
        if (spot == null) return false;
        lineup.addFront(spot.song);
        return true;
    }

    public List<String> showUpNext() {
        List<String> titles = new ArrayList<>();
        for (Song song : lineup) {
            if (songBook.containsKey(song.id)) titles.add(song.title);
        }
        return titles;
    }

    // ---------- Step 4: next ----------
    public Song next() {
        // Decide first: find the song to move to
        Song pick = null;
        while (!lineup.isEmpty()) {
            Song song = lineup.takeFront();
            if (songBook.containsKey(song.id)) { pick = song; break; } // skip removed
        }
        if (pick == null) {
            if (current == null) return null;
            SongNode following = songBook.get(current.id).after;
            if (following == null) return null; // nothing left, change nothing
            pick = following.song;
        }
        // Change second
        if (current != null) played.push(current);
        begin(pick);
        return current;
    }

    // ---------- Step 5: back ----------
    public Song back() {
        Song pick = null;
        while (!played.isEmpty()) {
            Song song = played.pop();
            if (songBook.containsKey(song.id)) { pick = song; break; } // skip removed
        }
        if (pick == null) return null;
        if (current != null) lineup.addFront(current); // next() brings it back
        begin(pick); // back() does NOT push onto history
        return current;
    }

    public List<String> showHistory() {
        List<String> titles = new ArrayList<>();
        for (Song song : played) {
            if (songBook.containsKey(song.id)) titles.add(song.title);
        }
        return titles;
    }

    // ---------- Step 6: remove ----------
    public boolean removeSong(String id) {
        SongNode spot = songBook.get(id);
        if (spot == null) return false;
        if (current != null && current.id.equals(id)) return false;

        // unlink: handles only-node, first, last and middle
        if (spot.before != null) spot.before.after = spot.after;
        else first = spot.after;
        if (spot.after != null) spot.after.before = spot.before;
        else last = spot.before;
        spot.before = spot.after = null;

        songBook.remove(id);
        tally.remove(id);
        return true;
    }

    // ---------- Demo: run this file to print the test output ----------
    static String t(Song s) { return s == null ? "null" : s.title; }
    public static void main(String[] args) {
        MusicPlayer p = new MusicPlayer();
        System.out.println("--- 1. Build the playlist ---");
        System.out.println(p.addSong("s1","Night Drive","A"));
        System.out.println(p.addSong("s2","Slow Tide","A"));
        System.out.println(p.addSong("s3","Copper Sky","A"));
        System.out.println(p.addSong("s4","Static Bloom","A"));
        System.out.println(p.addSong("s5","Low Orbit","A"));
        System.out.println(p.addSong("s6","Glass Rivers","A"));
        System.out.println("duplicate s3: " + p.addSong("s3","X","X"));
        System.out.println("playlist: " + p.showPlaylist());
        System.out.println("--- 2. Look up songs ---");
        System.out.println("find s4: " + t(p.findSong("s4")));
        System.out.println("find s9: " + t(p.findSong("s9")));
        System.out.println("--- 3. Play and move forward ---");
        System.out.println("play s1: " + t(p.play("s1")));
        System.out.println("play s9: " + t(p.play("s9")));
        System.out.println("next: " + t(p.next()));
        System.out.println("next: " + t(p.next()));
        System.out.println("history: " + p.showHistory());
        System.out.println("--- 4. Up next ---");
        System.out.println(p.queueLater("s5"));
        System.out.println(p.queueLater("s6"));
        System.out.println(p.queueNext("s1"));
        System.out.println("queue s9: " + p.queueLater("s9"));
        System.out.println("up next: " + p.showUpNext());
        System.out.println("next: " + t(p.next()));
        System.out.println("now playing: " + t(p.nowPlaying()));
        System.out.println("history: " + p.showHistory());
        System.out.println("--- 5. Back button ---");
        System.out.println("back: " + t(p.back()));
        System.out.println("up next: " + p.showUpNext());
        System.out.println("history: " + p.showHistory());
        System.out.println("--- 6. Remove songs ---");
        System.out.println("remove s5: " + p.removeSong("s5"));
        System.out.println("remove current (s3): " + p.removeSong("s3"));
        System.out.println("remove s9: " + p.removeSong("s9"));
        System.out.println("playlist: " + p.showPlaylist());
        System.out.println("up next: " + p.showUpNext());
        System.out.println("--- 7. Removed songs are skipped ---");
        System.out.println("next: " + t(p.next()));
        System.out.println("next: " + t(p.next()));
        System.out.println("next at end: " + t(p.next()));
        System.out.println("now playing: " + t(p.nowPlaying()));
        System.out.println("back: " + t(p.back()));
        System.out.println("up next: " + p.showUpNext());
        System.out.println("--- 8. Play counts ---");
        System.out.println("s1: " + p.playCount("s1"));
        System.out.println("s3: " + p.playCount("s3"));
        System.out.println("s6: " + p.playCount("s6"));
        System.out.println("s5 (removed): " + p.playCount("s5"));
        System.out.println("--- 9. Empty player ---");
        MusicPlayer e = new MusicPlayer();
        System.out.println("next: " + t(e.next()));
        System.out.println("back: " + t(e.back()));
        System.out.println("now playing: " + t(e.nowPlaying()));
        System.out.println("playlist: " + e.showPlaylist());
    }

}

// One song. Plain data holder.
class Song {
    public final String id;
    public final String title;
    public final String artist;

    public Song(String id, String title, String artist) {
        this.id = id;
        this.title = title;
        this.artist = artist;
    }

    @Override
    public String toString() {
        return title;
    }
}
