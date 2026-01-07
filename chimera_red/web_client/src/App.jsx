import React, { useState, useEffect, useRef } from 'react';
import { Terminal, Wifi, Bluetooth, Activity, Zap, ShieldAlert, Cpu } from 'lucide-react';

function App() {
    const [port, setPort] = useState(null);
    const [connected, setConnected] = useState(false);
    const [logs, setLogs] = useState([]);
    const [networks, setNetworks] = useState([]);
    const [bleDevices, setBleDevices] = useState([]);
    const [spectrum, setSpectrum] = useState([]);
    const [csiData, setCsiData] = useState([]);
    const [wardrive, setWardrive] = useState(false);
    const [gps, setGps] = useState(null);
    const [sessionLog, setSessionLog] = useState([]);
    const [activeTab, setActiveTab] = useState('dashboard');
    const [sysInfo, setSysInfo] = useState(null);


    const writerRef = useRef(null);
    const readerRef = useRef(null);
    const logEndRef = useRef(null);

    // Auto-scroll logs
    useEffect(() => {
        logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [logs]);

    const addLog = (msg) => {
        setLogs(prev => [...prev, `[${new Date().toLocaleTimeString()}] ${msg}`].slice(-100));
    };

    const connectSerial = async () => {
        if (!('serial' in navigator)) {
            alert("Web Serial API not supported! Use Chrome on Desktop or Android.");
            return;
        }
        try {
            const p = await navigator.serial.requestPort();
            await p.open({ baudRate: 115200 });
            setPort(p);
            setConnected(true);
            addLog("Connected to Serial Port");

            // Setup Writer
            const textEncoder = new TextEncoderStream();
            textEncoder.readable.pipeTo(p.writable); // pipe to port
            writerRef.current = textEncoder.writable.getWriter();

            // Setup Reader
            const textDecoder = new TextDecoderStream();
            p.readable.pipeTo(textDecoder.writable);
            readerRef.current = textDecoder.readable.getReader();

            readLoop();
        } catch (err) {
            console.error(err);
            addLog("Connection failed: " + err.message);
        }
    };

    const readLoop = async () => {
        while (true) {
            const { value, done } = await readerRef.current.read();
            if (done) break;
            if (value) {
                // Split by newlines as we might get chunks
                const lines = value.split('\n');
                for (const line of lines) {
                    if (!line.trim()) continue;
                    try {
                        // Try parsing JSON
                        if (line.trim().startsWith('{')) {
                            const data = JSON.parse(line);
                            handleData(data);
                        } else {
                            addLog("RAW: " + line);
                        }
                    } catch (e) {
                        // Not JSON, just raw text?
                        // addLog("RX: " + line); // Verbose
                    }
                }
            }
        }
    };

    // GPS Watcher
    useEffect(() => {
        let watchId;
        if (wardrive) {
            watchId = navigator.geolocation.watchPosition(
                (pos) => setGps({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
                (err) => addLog('GPS Error: ' + err.message),
                { enableHighAccuracy: true }
            );
            addLog("GPS Tracking Enabled");
        } else {
            if (watchId) navigator.geolocation.clearWatch(watchId);
            setGps(null);
        }
        return () => navigator.geolocation.clearWatch(watchId);
    }, [wardrive]);

    const handleData = (data) => {
        if (data.type === 'wifi_scan_result') {
            const nets = data.networks || [];
            setNetworks(nets);
            addLog(`Found ${data.count} Wi-Fi networks`);

            // Wardrive Log logic
            if (wardrive && gps) {
                const newLogs = nets.map(n => ({ ...n, lat: gps.latitude, lon: gps.longitude, timestamp: new Date().toISOString() }));
                setSessionLog(prev => [...prev, ...newLogs]);
                addLog(`Logged ${nets.length} nets with GPS`);
            }
        } else if (data.type === 'ble_scan_result') {
            setBleDevices(data.devices || []);
            addLog(`Found ${data.count} BLE devices`);
        } else if (data.type === 'spectrum_result') {
            setSpectrum(data.data || []);
            addLog('Spectrum Scan Complete');
        } else if (data.type === 'csi') {
            // Keep last 50 samples for waterfall
            setCsiData(prev => [...prev, data].slice(-50));
        } else if (data.type === 'sys_info') {
            setSysInfo(data);
            addLog(`System Info: ${data.chip}`);
        } else if (data.msg) {
            addLog(data.msg);
        }
    };

    const toggleWardrive = () => {
        setWardrive(!wardrive);
        // If starting, request permission/location immediately
        if (!wardrive) {
            navigator.geolocation.getCurrentPosition(() => { }, (e) => alert(e.message));
        }
    };

    const exportLogs = () => {
        const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(sessionLog, null, 2));
        const downloadAnchorNode = document.createElement('a');
        downloadAnchorNode.setAttribute("href", dataStr);
        downloadAnchorNode.setAttribute("download", "wardrive_log.json");
        document.body.appendChild(downloadAnchorNode); // required for firefox
        downloadAnchorNode.click();
        downloadAnchorNode.remove();
    };

    const sendCommand = async (cmd) => {
        if (!writerRef.current) return;
        try {
            await writerRef.current.write(cmd + "\n");
            addLog("TX: " + cmd);
        } catch (err) {
            addLog("TX Error: " + err.message);
        }
    };

    return (
        <div className="app-container">
            <header className="glass">
                <div className="logo">
                    <h1>CHIMERA<span style={{ color: '#fff' }}>RED</span></h1>
                </div>
                <div className="controls">
                    {!connected ? (
                        <button className="btn" onClick={connectSerial}>Connect Device</button>
                    ) : (
                        <span className="badge" style={{ color: '#0f0', border: '1px solid #0f0' }}>CONNECTED</span>
                    )}
                </div>
            </header>

            <div className="toolbar glass" style={{ margin: '20px 20px 0 20px', padding: '10px', display: 'flex', gap: '10px' }}>
                <button className={`btn ${activeTab === 'dashboard' ? '' : 'btn-secondary'}`} onClick={() => setActiveTab('dashboard')}>Dashboard</button>
                <button className={`btn ${activeTab === 'wifi' ? '' : 'btn-secondary'}`} onClick={() => setActiveTab('wifi')}>Wi-Fi</button>
                <button className={`btn ${activeTab === 'ble' ? '' : 'btn-secondary'}`} onClick={() => setActiveTab('ble')}>BLE</button>
                <button className={`btn ${activeTab === 'terminal' ? '' : 'btn-secondary'}`} onClick={() => setActiveTab('terminal')}>Terminal</button>
            </div>

            <main>
                {activeTab === 'dashboard' && (
                    <>
                        <div className="card glass">
                            <h2><Activity size={20} /> System Status</h2>
                            <div className="status-grid">
                                <div className="list-item">
                                    <span>Device</span>
                                    <span>{sysInfo ? sysInfo.chip : 'N/A'}</span>
                                </div>
                                <div className="list-item">
                                    <span>MAC</span>
                                    <span>{sysInfo ? sysInfo.mac : 'N/A'}</span>
                                </div>
                                <div className="list-item">
                                    <span>Connection</span>
                                    <span>{connected ? 'USB Serial' : 'Disconnected'}</span>
                                </div>
                            </div>
                            <button className="btn" onClick={() => sendCommand('GET_INFO')}>Refresh Info</button>
                        </div>

                        <div className="card glass">
                            <h2><Zap size={20} /> Quick Actions</h2>
                            <div style={{ display: 'grid', gap: '10px', gridTemplateColumns: '1fr 1fr' }}>
                                <button className="btn" onClick={() => sendCommand('SCAN_WIFI')}>Scan Wi-Fi</button>
                                <button className="btn" onClick={() => sendCommand('SCAN_BLE')}>Scan BLE</button>
                            </div>
                        </div>
                    </>
                )}

                {activeTab === 'wifi' && (
                    <div className="card glass">
                        <div className="status-bar">
                            <h2><Wifi size={20} /> Wi-Fi Recon</h2>
                            <button className="btn" onClick={() => sendCommand('SCAN_WIFI')}>SCAN</button>
                        </div>
                        <div className="list-container">
                            {networks.map((net, i) => (
                                <div key={i} className="list-item">
                                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                                        <strong style={{ color: 'var(--primary)' }}>{net.ssid || '<Hidden>'}</strong>
                                        <span style={{ fontSize: '0.8em', color: '#888' }}>CH {net.channel} | {net.encryption === 7 ? 'OPEN' : 'SECURE'}</span>
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                        <span className="badge">{net.rssi} dBm</span>
                                    </div>
                                </div>
                            ))}
                            {networks.length === 0 && <p style={{ padding: '20px', textAlign: 'center', color: '#555' }}>No networks found. Start a scan.</p>}
                        </div>
                    </div>
                )}

                {activeTab === 'ble' && (
                    <div className="card glass">
                        <div className="status-bar">
                            <h2><Bluetooth size={20} /> BLE Recon</h2>
                            <button className="btn" onClick={() => sendCommand('SCAN_BLE')}>SCAN</button>
                        </div>
                        <div className="list-container">
                            {bleDevices.map((dev, i) => (
                                <div key={i} className="list-item">
                                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                                        <strong style={{ color: 'var(--secondary)' }}>{dev.name || 'Unknown Device'}</strong>
                                        <span style={{ fontSize: '0.8em', color: '#888' }}>{dev.address}</span>
                                    </div>
                                    <span className="badge">{dev.rssi} dBm</span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}



                {activeTab === 'csi' && (
                    <div className="card glass">
                        <div className="status-bar">
                            <h2><Activity size={20} /> WiFi Sensing (CSI Radar)</h2>
                            <div style={{ display: 'flex', gap: '10px' }}>
                                <button className="btn" onClick={() => sendCommand('START_CSI')}>START</button>
                                <button className="btn btn-secondary" onClick={() => sendCommand('STOP_CSI')}>STOP</button>
                            </div>
                        </div>
                        <div style={{
                            height: '300px',
                            background: '#000',
                            borderRadius: '8px',
                            overflow: 'hidden',
                            position: 'relative',
                            border: '1px solid var(--border)'
                        }}>
                            {/* Simplified Waterfall Visualization */}
                            {csiData.slice(-50).map((row, i) => (
                                <div key={i} style={{ display: 'flex', height: '6px', width: '100%', opacity: (i / 50) }} >
                                    {row.data.map((amp, j) => {
                                        // Subcarrier Heatmap
                                        // Amp usually 0-127. Map to Color.
                                        const intensity = Math.min(255, amp * 5); // Gain
                                        const color = `rgb(${intensity}, ${255 - intensity}, 50)`;
                                        return (
                                            <div key={j} style={{ flex: 1, background: color }}></div>
                                        )
                                    })}
                                </div>
                            ))}
                            <div style={{ position: 'absolute', bottom: 10, left: 10, color: '#0f0', fontSize: '0.8em', background: 'rgba(0,0,0,0.5)', padding: '4px' }}>
                        Subcarriers (Frequency Domain) ->
                            </div>
                        </div>
                        <p style={{ fontSize: '0.8em', color: '#aaa', marginTop: '10px' }}>
                            Visualizing multipath distortions (motion) in the environment.
                            <br />Blue/Green = Steady state. Yellow/Red = Motion/Interference.
                        </p>
                    </div>
                )}

                {activeTab === 'terminal' && (
                    <div className="card glass" style={{ height: '100%' }}>
                        <h2><Terminal size={20} /> Serial Log</h2>
                        <div className="log-terminal">
                            {logs.map((log, i) => (
                                <div key={i}>{log}</div>
                            ))}
                            <div ref={logEndRef} />
                        </div>
                        <div style={{ marginTop: '10px', display: 'flex', gap: '10px' }}>
                            <input type="text"
                                style={{ flex: 1, padding: '10px', background: 'transparent', border: '1px solid #333', color: '#fff', borderRadius: '4px' }}
                                placeholder="Send raw command..."
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter') {
                                        sendCommand(e.target.value);
                                        e.target.value = '';
                                    }
                                }}
                            />
                        </div>
                    </div>
                )}
            </main>
        </div>
    )
}

export default App
