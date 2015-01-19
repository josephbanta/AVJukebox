**IMPORTANT! The Spotify Android SDK is currently a beta release; its content
and functionality are likely to change significantly and without warning.**

Spotify Android SDK
===================

Welcome to Spotify Android SDK! This project is for people who wish to develop
Android applications containing Spotify-related functionality, such as audio streaming and
user authentication and authorization.

Note that by using this SDK, you accept our [Developer Terms of
Use](https://developer.spotify.com/developer-terms-of-use/).


Beta Release Information
========================

We're releasing this SDK early to gain feedback from the developer community
about the future of our Android SDK. Please file feedback about missing issues
or bugs over at our [issue tracker](https://github.com/spotify/android-sdk/issues),
making sure you search for existing issues and adding your voice to those rather
than duplicating.

For known issues and release notes, see the
[CHANGELOG.md](https://github.com/spotify/android-sdk/blob/master/CHANGELOG.md)
file.


Getting Started
===============

Please see the [beginner's
tutorial](https://developer.spotify.com/technologies/spotify-android-sdk/tutorial/)
on the Spotify Developer Website.


Authenticating and Scopes
=========================

You can generate your application's Client ID, Client Secret and define your
callback URIs at the [My Applications](https://developer.spotify.com/my-applications/)
section of the Spotify Developer Website.

When connecting a user to your app, you *must* provide the scopes your
application needs to operate. A scope is a permission to access a certain part
of a user's account, and if you don't ask for the scopes you need you will
receive permission denied errors when trying to perform various tasks.

You do *not* need a scope to access non-user specific information, such as to
perform searches, look up metadata, etc. A full list of scopes can be found on
[Scopes](https://developer.spotify.com/web-api/using-scopes/) section of the
Spotify Developer Website.

If your application's scope needs change after a user is connected to your app, you
will need to throw out your stored credentials and re-authenticate the user with the
new scopes.

**Important:** Only ask for the scopes your application needs. Requesting playlist
access when your app doesn't use playlists, for example, is bad form.
