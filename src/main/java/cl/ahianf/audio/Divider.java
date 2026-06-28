package cl.ahianf.audio;


public class Divider {
    private int period;
    private int counter;

    public Divider(int period) {
        this.period = period;
        this.counter = 0;
    }

    public boolean clock() {
        if (counter == 0) {
            counter = period;
            return true;
        }

        counter--;
        return false;
    }

    public void set_period(int p) {
        this.period = p;
    }

    public void reset() {
        this.counter = period;
    }

    public int get_period() {
        return period;
    }
}