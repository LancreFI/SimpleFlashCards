<pre>Simple flash cards app for Android

Just create your wordlists.json for flashcards in the following format:

{ 
	"source language": "English", 
	"destination language": "Finnish", 
	"words": { 
						"you": "sinä", 
						"I": "minä", 
						"because": "koska" 
	} 
}

You can add the wordlists from a remote server by entering the URL for the list or import a local 
copy.

After you've added a wordlist, you can long press the list name, to access the related 
functionalities.
	
Just some quick vibe coding project as my notebook for my current language studies is falling apart 
already.
</pre>

----
<pre>
Updates 050726:
    -improved character validation accuracy using the Fréchet distance algorithm
    -added Edit and Delete actions directly to the character quiz
    -added Undo support to the character quiz drawing view
    -increased the speed of the stroke animation for incorrect answers
</pre>

<pre>
Updates 020726:
    -fixed a crash bug in character drawing quiz and added home button
</pre>

<pre>
Updates 28 and 290626:
    -made some pretty icons instead of ugly text buttons on the flashcards
     -added the icon for switching languages
     -added the icon and capability to edit the words on the fly
    -added the functionality of adding a hand drawn character
     -also contains the ability to track the order of strokes for the character drawing
      and the direction of the strokes
     -handy for example in studying hiragana/katakana/kanji etc.
    -the character quiz was added, where the user draws the character asked for and tries to get the
     potential stroke order and direction correctly
     -wrong guess will display an animation of how the character should be drawn
</pre>

<pre>
Update 170426:
    Fixed:
        -the flash cards won't reset when screen is rotated
        -the adding of words to a review list using the star-icon works again
</pre>

<pre>
Update 150426:
	Added: 
		-added the option to create an empty wordlist when adding a new wordlist
		-added the possibility to delete words during the flashcard activity
        -added the option to search for words from a wordlist, matching partials too
    Fixed:
        -word order resetting from "in order" to "random" when restarting a flashcard activity
</pre>

<pre>
Update 080426:
	Added: 
		-added the option to go through the words in the order they were added or by random
		-added the option to browse the words backward/forward
        -added the option to swap the source and destination languages around
        -added word tagging, where you can tag/star a word, which will pick it up to a separate
         review list, so if you have problems remembering only certain words, you can now easily 
         pick them to a list of their own to practice on.
         This list is also editable and has all the same functionalities the "normal" word lists 
         have
</pre>

<pre>
Update 070426:
	Added: 
		-add/remove words from wordlist
		-share/export wordlists
	Fixed: Some bugs in positioning the elements in widescreen mode. NOTE: rotating screen resets 
           the current flashcard set progress.
</pre>
