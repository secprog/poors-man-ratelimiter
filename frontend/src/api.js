import axios from 'axios';

const api = axios.create({
    baseURL: '/api', // Public gateway APIs on port 8080
    headers: {
        'Content-Type': 'application/json',
    },
});

export default api;
