import type { NextConfig } from "next";
const nextConfig: NextConfig = {
	output: "standalone",
	headers() {
		return Promise.resolve([{ 
			source: "/(.*)",
			headers: [
				{ key: "Content-Security-Policy", value: "default-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; script-src 'self' 'unsafe-inline' 'unsafe-eval'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'" },
				{ key: "X-Content-Type-Options", value: "nosniff" },
				{ key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
				{ key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" }
			]
		}]);
	}
};
export default nextConfig;
