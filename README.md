# Music Player API

This project is a Spring Boot RESTful API for a Youtube music player application using FFMPEG.
It allows users to register and then to manage their music library, create playlists, stream songs from Youtube or only
selected parts of them and share
the prepared tracks with other users.
The API provides endpoints for user authentication, music management, playlist management, and track sharing.

My primary goal was to create a simple API that can handle demands for quick music management during D&D sessions.
Thus, every track can be assigned to a board which serves as a control panel for controlling the flow of the music in
real-time with minimum effort.

## Local run

To run the project locally, follow these steps:

1. Clone the repository and navigate to the project directory
2. ```docker compose up --build```
    - This command will build the Docker images for application and database and start the containers for the
      application.
3. For remote development separate Postgres container is needed. Change the jwt secret in the properties file and add
   the necessary environment variables
   for the database connection.







