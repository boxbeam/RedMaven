package redempt.redmaven;

import java.util.function.Consumer;

public interface WrappedConsumer<T> {

	public static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
		throw (T) t;
	}
	
	public static <T> Consumer<T> wrap(WrappedConsumer<T> wrapped) {
		return v -> {
			try {
				wrapped.accept(v);
			} catch (Throwable t) {
				sneakyThrow(t);
			}
		};
	}
	
	public void accept(T value) throws Throwable;

}
