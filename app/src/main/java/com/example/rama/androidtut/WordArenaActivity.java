package com.example.rama.androidtut;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.example.rama.androidtut.UtilityClasses.ChallengeManager;
import com.example.rama.androidtut.UtilityClasses.LetterAdapter;
import com.example.rama.androidtut.UtilityClasses.LetterValues;
import com.example.rama.androidtut.UtilityClasses.Score;
import com.example.rama.androidtut.UtilityClasses.Trie;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;


/**
 * Activity for word creation. UI displays all collected letters and allows creation of
 * 7 letter words using available letters.
 */
public class WordArenaActivity extends AppCompatActivity {

    static String TAG = "WordArenaActivity";
    int chosen = 0;
    DatabaseReference gamePlayDb;
    private int score;
    //text view containing 7 characters to be filled by the user
    private TextView[] charViews;
    //submit button
    private Button submitButton;
    private Button hintButton;
    //pop window when pressing submit
    private PopupWindow pwindo;
    //letter adapter for displaying letter inventory
    private LetterAdapter ltrAdapt;
    //challenge manager to be notified when a new word is created
    private ChallengeManager challengeManager;
    /**
     * Database references
     */
    private DatabaseReference[] letterRefs;
    private DatabaseReference scoreDb;
    private DatabaseReference hintsDb;
    //leter inventory counters used for display
    private int[] temporaryCount;
    //letter inventory counters used for actual database values
    private int[] letterCounts;
    //number of hints
    private int noHints;
    //dictionary storing possible words
    private Trie dictionary;
    //broadcat receiver to monitor internet connection(needed for maintaining database state)
    private BroadcastReceiver internetBroadcastReceiver;
    private ValueEventListener scoreEventLinstener;
    private ValueEventListener hintsEventListener;

    /**
     * Calculate score of a word based on the sum of the value of its letters
     *
     * @param word word
     * @return score
     */
    private static int calculateScore(String word) {
        int sc = 0;
        for (int i = 0; i < word.length(); i++) {
            sc += LetterValues.getValue(word.charAt(i));
        }
        return sc;
    }

    /**
     * Get view and database connections
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_arena);

        //get view resources and initialize variables
        submitButton = (Button) findViewById(R.id.yes);
        hintButton = (Button) findViewById(R.id.hints);
        LinearLayout wordLayout = (LinearLayout) findViewById(R.id.word);
        GridView letters = (GridView) findViewById(R.id.letters);

        //initialize variables
        charViews = new TextView[7];
        ltrAdapt = new LetterAdapter(this);
        letters.setAdapter(ltrAdapt);
        submitButton.setEnabled(false);

        //loop through characters
        for (int c = 0; c < 7; c++) {
            charViews[c] = new TextView(this);
            charViews[c].setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
            charViews[c].setGravity(Gravity.CENTER);
            charViews[c].setBackgroundResource(R.drawable.letter_bg);
            charViews[c].setTextSize(20);
            //add to display
            wordLayout.addView(charViews[c]);

        }

        //initialize database references and get values
        DatabaseReference database = FirebaseDatabase.getInstance().getReference();
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        FirebaseUser user = firebaseAuth.getCurrentUser();
        loadDictionary();
        scoreDb = database.child("Scores").child(user.getUid()).getRef();


        hintsDb = database.child("Statistics").child(user.getUid()).child("NumberOfHints").getRef();


        gamePlayDb = database.child("GamePlay").child(user.getUid());
        letterCounts = new int[26];
        temporaryCount = new int[26];
        letterRefs = new DatabaseReference[26];

        challengeManager = ChallengeManager.getInstance();


    }

    /**
     * Load the dictionary file in memory, using a trie as a data structure
     */
    private void loadDictionary() {
        dictionary = new Trie();
        try {
            InputStream inputStream = getResources().openRawResource(R.raw.grabdict);
            Scanner scanner = new Scanner(inputStream);
            while (scanner.hasNextLine()) {
                String word = scanner.nextLine().toUpperCase();
                dictionary.add(word);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not load dictionary", e);
        }
    }

    /**
     * React to user pressing a letter by adding it to the current word being created
     *
     * @param view letter button
     */
    public void letterPressed(View view) {
        if (chosen >= 7) return;
        //user has pressed a letter to guess
        String ltr = ((TextView) view).getText().toString();

        //can only press letter if more than 0 count
        if (temporaryCount[ltr.charAt(0) - 'A'] <= 0) return;
        String newText = ltr.charAt(0) + ":" + (temporaryCount[ltr.charAt(0) - 'A'] - 1);
        temporaryCount[ltr.charAt(0) - 'A']--;
        ((TextView) view).setText(newText);
        char letterChar = ltr.charAt(0);
        // view.setBackgroundResource(R.drawable.letter_down);
        if (chosen < 7) {
            charViews[chosen].setText(String.valueOf(letterChar));
            charViews[chosen].setTextColor(Color.BLACK);
            chosen++;
        }
        if (chosen == 7) {
            submitButton.setEnabled(true);
        }
    }

    @Override

    protected void onStop() {

        super.onStop();
        Log.i(TAG, "OnStop");

    }

    /**
     * Remove database event listeners
     */
    @Override
    protected void onPause() {
        super.onPause();
        scoreDb.removeEventListener(scoreEventLinstener);
        hintsDb.removeEventListener(hintsEventListener);
        challengeManager.removeListeners();
        unregisterReceiver(internetBroadcastReceiver);
        Log.i(TAG, "Paused");

    }

    /**
     * Add database event listeners
     */
    @Override
    protected void onResume() {

        super.onResume();

        Log.i(TAG, "Resumed");
        for (int i = 0; i < 26; i++) {
            final int j = i;
            String letter = (char) (i + 'A') + "";
            letterRefs[i] = gamePlayDb.child("Letters").child(letter).getRef();

            letterRefs[i].addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    letterCounts[j] = dataSnapshot.getValue(Integer.class);
                    temporaryCount[j] = letterCounts[j];
                    ltrAdapt.updateCount(j, letterCounts[j]);
                    ltrAdapt.notifyDataSetChanged();

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, databaseError.getMessage());
                }
            });
        }

        scoreDb.addValueEventListener(scoreEventLinstener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                score = dataSnapshot.getValue(Score.class).getScore();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

                Log.e(TAG, databaseError.getMessage());
            }
        });

        hintsDb.addValueEventListener(hintsEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                noHints = dataSnapshot.getValue(Integer.class);
                String s = "Hints: " + noHints;
                hintButton.setText(s);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        installInternetListener();
        challengeManager.removeListeners();

    }


    /**
     * Reset current word
     * @param view clear button
     */
    public void refreshScreen(View view) {
        ltrAdapt.reset(letterCounts);
        for (int i = 0; i < 7; i++)
            charViews[i].setText("");
        chosen = 0;
        System.arraycopy(letterCounts, 0, temporaryCount, 0, letterCounts.length);
    }

    /**
     * Check whether inputed word is present in the dictionary and trigger award events in
     * a pop up window
     * @param view submit button
     */
    public void checkWord(View view) {
        LayoutInflater inflater = (LayoutInflater) this
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.popup_word,
                (ViewGroup) findViewById(R.id.show_word));
        pwindo = new PopupWindow(layout, 400, 300, true);
        pwindo.showAtLocation(layout, Gravity.CENTER, 0, 0);
        pwindo.setBackgroundDrawable(new ColorDrawable());
        FloatingActionButton fab_close = (FloatingActionButton) layout.findViewById(R.id.welldone_cancel);
        fab_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pwindo.dismiss();

            }
        });
        TextView textView = (TextView) layout.findViewById(R.id.tvcheckWord);
        String word = getCurrentWord();
        String response = word + " is not a valid word. Try again!";
        if (dictionary.contains(word)) {

            int scoreToAdd = calculateScore(word);
            response = word + "\n" + scoreToAdd + " points";
            int newScore = score + scoreToAdd;
            challengeManager.checkWord(word, this, scoreToAdd);
            challengeManager.checkScore(newScore, this);
            scoreDb.child("score").setValue(newScore);
            updateCounts(word);

        }
        textView.setTextSize(18);
        textView.setText(response);
        refreshScreen(view);

    }

    /**
     *
     * @return current input word
     */
    private String getCurrentWord() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++)
            sb.append(charViews[i].getText());
        return sb.toString().toUpperCase();
    }

    /**
     * Update database values by substracting used letters
     * @param word current input
     */
    private void updateCounts(String word) {
        for (int i = 0; i < word.length(); i++) {
            int pos = word.charAt(i) - 'A';
            int t = temporaryCount[pos];
            letterRefs[pos].setValue(t);
            letterCounts[pos] = t;
        }
    }

    /**
     * Complete current input word, provided at least 2 letters have been inputed.
     * Check whether there is a completion with the available letters and display that as a hint,
     * otherwise suggets a word that needs more letters to be collected.
     * @param view hint button
     */
    public void giveHint(View view) {

        if (noHints >= 1) {
            String prefix = getCurrentWord();
            String message = "You need to input at least 2 letters to get a hint.";
            if (prefix.length() >= 2) {
                hintsDb.setValue(noHints - 1);
                List<String> res = dictionary.getAllWordsStartingWith(prefix);
                Log.w(TAG, prefix);
                if (res == null || res.size() == 0) message = "No completion found.";
                else {
                    String match = checkMatch(res);
                    if (match == null)
                        message = "No completion found. With a few more letters you could form " + res.get(0);
                    else message = "How about " + match + "?";
                }
            }
            AlertDialog alertDialog = BaseActivity.getAlertDialog(this);
            alertDialog.setMessage(message);
            alertDialog.setTitle("Hint");
            alertDialog.show();
        }
    }

    /**
     * Helper function for checking whether any of the suggested words can be formed with
     * the letters the user has in inventory
     * @param res result list of suggested words
     * @return a suggested word or null
     */
    private String checkMatch(List<String> res) {
        Map<Character, Integer> counter = new HashMap<>();
        for (String s : res) {
            counter.clear();
            boolean found = true;
            for (int i = 0; i < 26; i++) {
                counter.put(((char) (i + 'A')), temporaryCount[i]);
            }
            for (int i = 0; i < s.length(); i++) {
                if (counter.get(s.charAt(i)) > 0) {
                    counter.put(s.charAt(i), counter.get(s.charAt(i)) - 1);
                } else found = false;
            }
            if (found) {
                return s;
            }
        }
        return null;
    }


    /**
     * Install internet listener
     */
    private void installInternetListener() {

        if (internetBroadcastReceiver == null) {

            internetBroadcastReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {

                    Bundle extras = intent.getExtras();

                    NetworkInfo info = extras
                            .getParcelable("networkInfo");

                    NetworkInfo.State state = info.getState();

                    if (state != NetworkInfo.State.CONNECTED) {

                        Toast.makeText(getApplicationContext(), "Internet connection is off. Turn it on to save your progress", Toast.LENGTH_LONG).show();


                    }
                }
            };

        }
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(internetBroadcastReceiver, intentFilter);
    }
}
