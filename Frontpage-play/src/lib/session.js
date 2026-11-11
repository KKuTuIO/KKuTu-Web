let authRequest;
let blockRequest;
const userRequests = new Map();

async function fetchJson(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) throw new Error(`Request failed (${response.status})`);
    return response.json();
}

export function loadAuth() {
    if (!authRequest) {
        authRequest = fetchJson('/user/oauth').catch(error => {
            authRequest = undefined;
            throw error;
        });
    }
    return authRequest;
}

export function loadUser(userId) {
    if (!userRequests.has(userId)) {
        const request = fetchJson(`/user/${encodeURIComponent(userId)}`).catch(error => {
            if (userRequests.get(userId) === request) userRequests.delete(userId);
            throw error;
        });
        userRequests.set(userId, request);
    }
    return userRequests.get(userId);
}

export function loadBlock() {
    if (!blockRequest) {
        blockRequest = fetchJson('/api/block', {cache: 'no-store'}).catch(error => {
            blockRequest = undefined;
            throw error;
        });
    }
    return blockRequest;
}
