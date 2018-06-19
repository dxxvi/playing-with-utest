const PROXY_CONFIG = [
  {
    context: [
      "/api", "/ws"
    ],
    target: "http://localhost:4567",
    secure: false,
    ws: true
  }
];

module.exports = PROXY_CONFIG;
