package grapheneos.srtpermtests.internet.appthataccessesinternet;

interface IAccessInternetOnCommand {
    /** Access internet on command */
    void accessInternet();

    boolean isConnected();

    boolean getSensorInfo();
}
