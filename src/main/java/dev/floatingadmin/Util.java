package dev.floatingadmin;

/** Small shared helpers. */
final class Util {
	private Util() {}

	static String duration(long millis) {
		long s = Math.max(0, millis / 1000);
		long d = s / 86400;
		long h = (s % 86400) / 3600;
		long m = (s % 3600) / 60;
		if (d > 0) {
			return d + "d " + h + "h";
		}
		if (h > 0) {
			return h + "h " + m + "m";
		}
		if (m > 0) {
			return m + "m " + (s % 60) + "s";
		}
		return s + "s";
	}
}
