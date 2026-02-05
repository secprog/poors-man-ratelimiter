import axios from 'axios';

const adminApi = axios.create({
    baseURL: '/poormansRateLimit/api', // Admin APIs on separate port 9090 via Nginx proxy
    headers: {
        'Content-Type': 'application/json',
    },
});

export default adminApi;
