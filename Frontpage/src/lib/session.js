let authRequest;
let blockRequest;
const userRequests = new Map();

export function loadAuth() {
    if (!authRequest) authRequest = fetch('/user/oauth').then(response => response.json());
    return authRequest;
}

export function loadUser(userId) {
    if (!userRequests.has(userId)) userRequests.set(userId, fetch(`/user/${userId}`).then(response => response.json()));
    return userRequests.get(userId);
}

export function loadBlock() {
    if (!blockRequest) blockRequest = fetch('/api/block', {cache: 'no-store'}).then(response => response.json());
    return blockRequest;
}
