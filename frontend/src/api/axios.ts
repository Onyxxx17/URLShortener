import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true,
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 429) {
      error.response.data = {
        message: typeof error.response.data === 'string' 
          ? error.response.data 
          : 'Too many requests. Please try again later.'
      };
    }
    return Promise.reject(error);
  }
);

export default api;
