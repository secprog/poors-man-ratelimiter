import axios from 'axios';

const api = axios.create({
    baseURL: '/api', // Use Nginx proxy to avoid CORS issues
    headers: {
        'Content-Type': 'application/json',
    },
});

export default api;
