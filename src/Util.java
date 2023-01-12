public class Util {

	/**
	 * Splits Strings properly
	 * @param string String which should be spilt
	 * @param delimiter String representing the separator
	 * @return Split String
	 */
	public static String[] split(String string, String delimiter) {
		if(string.isEmpty())
			return new String[0];
		return string.split(delimiter, -1);
	}
}
