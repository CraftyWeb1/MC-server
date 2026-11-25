package autism.serverbridge.trade;

import java.util.UUID;

public final class TradeSession {
    public final UUID id = UUID.randomUUID();
    public final UUID a;
    public final UUID b;
    public final String nameA;
    public final String nameB;
    public double yapA;
    public double brainA;
    public double yapB;
    public double brainB;
    public boolean confirmA;
    public boolean confirmB;
    public boolean completed;
    public boolean closing;
    public TradeInventory inventory;

    public TradeSession(UUID a, String nameA, UUID b, String nameB) {
        this.a = a;
        this.b = b;
        this.nameA = nameA;
        this.nameB = nameB;
    }

    public boolean involves(UUID id) {
        return a.equals(id) || b.equals(id);
    }

    public void resetConfirm() {
        confirmA = false;
        confirmB = false;
    }
}
