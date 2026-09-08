// Dev: gọi qua đường dẫn tương đối /api, dev-server proxy sang localhost:8080 (proxy.conf.json)
export const environment = {
  production: false,
  apiUrl: '/api',
  /**
   * OAuth Client ID from Google Cloud Console. Empty hides the Google
   * button entirely and keeps their script off the page - the backend
   * refuses the endpoint without its own copy anyway.
   */
  googleClientId: '',
  /**
   * Whether the deployment has Zalo credentials. Only decides whether the
   * button is offered - the backend refuses the endpoints regardless if its
   * own app id and secret are missing.
   */
  zaloEnabled: false,
};
