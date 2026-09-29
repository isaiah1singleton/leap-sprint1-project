// API Configuration for frontend-backend communication
// Update API_URL based on your environment (development, staging, production)

export const API_CONFIG = {
  // Backend API base URL
  // For local development: http://localhost:8080
  // For production: update to your production URL
  baseUrl: 'http://10.18.72.7:8082',

  // API endpoints
  endpoints: {
    auth: {
      signIn: '/api/auth/sign-in',
      register: '/api/auth/register',
    },
    accounts: {
      list: '/api/accounts',
      get: '/api/accounts/:id',
      create: '/api/accounts',
    },
  },
};
