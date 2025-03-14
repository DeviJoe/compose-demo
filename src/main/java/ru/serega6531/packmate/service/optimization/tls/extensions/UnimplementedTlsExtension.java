package ru.serega6531.packmate.service.optimization.tls.extensions;

import ru.serega6531.packmate.service.optimization.tls.numbers.ExtensionType;

public class UnimplementedTlsExtension extends TlsExtension {

    private byte[] data;

    public UnimplementedTlsExtension(ExtensionType type, byte[] rawData, int offset, short extensionLength) {
        super(type, extensionLength);

        data = new byte[extensionLength];
        System.arraycopy(rawData, offset, data, 0, extensionLength);
    }

    @Override
    public String toString() {
        if(extensionLength > 0) {
            return type.name() + " [" + extensionLength + " bytes]";
        } else {
            return type.name();
        }
    }
}
