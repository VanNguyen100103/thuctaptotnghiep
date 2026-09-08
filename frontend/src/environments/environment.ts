export const environment = {
  production: true,
  apiUrl: 'https://thuctaptotnghiep-backend.onrender.com/api',
  /**
   * OAuth Client ID from Google Cloud Console. Empty hides the Google
   * button entirely and keeps their script off the page - the backend
   * refuses the endpoint without its own copy anyway.
   */
  googleClientId: '210830835681-i33csv8o188ksllnrift6cqt41ts9vj0.apps.googleusercontent.com',
  /**
   * Whether the deployment has Zalo credentials. Only decides whether the
   * button is offered - the backend refuses the endpoints regardless if its
   * own app id and secret are missing.
   */
  zaloEnabled: true,
};
