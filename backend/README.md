# TarixUstoz Backend (Django)

This is the fully autonomous backend for the TarixUstoz Android application.

## Setup Instructions

Open your Android Studio Terminal and run the following commands:

1. Go into the backend directory:
   ```bash
   cd backend
   ```

2. Create a virtual environment:
   ```bash
   python -m venv venv
   ```

3. Activate the virtual environment:
   - On Windows: `venv\Scripts\activate`
   - On Mac/Linux: `source venv/bin/activate`

4. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

5. Create the Django project (don't forget the dot `.` at the end):
   ```bash
   django-admin startproject core .
   ```

6. Create an app for your history content:
   ```bash
   python manage.py startapp api
   ```

7. Run initial migrations:
   ```bash
   python manage.py migrate
   ```

8. Create an admin user:
   ```bash
   python manage.py createsuperuser
   ```

9. Start the server:
   ```bash
   python manage.py runserver
   ```
