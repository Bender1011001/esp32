import React, { useState, useEffect, useRef } from 'react';
import { Terminal, Wifi, Bluetooth, Activity, Zap, ShieldAlert, Cpu, Globe, Search, Target, X } from 'lucide-react';
import { Dashboard } from './components/Dashboard';
import { WiFiPanel } from './components/WiFiPanel';
import { BLEPanel } from './components/BLEPanel';
import { WiglePanel } from './components/WiglePanel';
import { CSIPanel } from './components/CSIPanel';
import { TerminalView } from './components/TerminalView';

function App() {
    const [port, setPort] = useState(null);
    const [connected, setConnected] = useState(false);
    const [logs, setLogs] = useState([]);
    const [networks, setNetworks] = useState([]);
    const [bleDevices, setBleDevices] = useState([]);
    const [spectrum, setSpectrum] = useState([]);
    const [csiData, setCsiData] = useState([]);
    const [activeTab, setActiveTab] = useState('dashboard');
    const [sysInfo, setSysInfo] = useState(null);
    const [trackedDevice, setTrackedDevice] = useState(null);

    const writerRef = useRef(null);
    const readerRef = useRef(null);

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

            const textEncoder = new TextEncoderStream();
            textEncoder.readable.pipeTo(p.writable);
            writerRef.current = textEncoder.writable.getWriter();

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
        let readBuffer = "";
        while (true) {
            const { value, done } = await readerRef.current.read();
            if (done) break;
            if (value) {
                readBuffer += value;
                const lines = readBuffer.split('\n');
                readBuffer = lines.pop();

                for (const line of lines) {
                    if (!line.trim()) continue;
                    try {
                        if (line.trim().startsWith('{')) {
                            const data = JSON.parse(line);
                            handleData(data);
                        } else {
                            addLog("RAW: " + line);
                        }
                    } catch (e) {
                        addLog("Parse Error: " + line);
                    }
                }
            }
        }
    };

    const handleData = (data) => {
        if (data.type === 'wifi_scan_result') {
            const nets = data.networks || [];
            setNetworks(nets);

            if (trackedDevice && trackedDevice.type === 'wifi') {
                const target = nets.find(n => n.bssid === trackedDevice.id || n.ssid === trackedDevice.id);
                if (target) {
                    updateTrackedRSS(target.rssi);
                }
            }
            addLog(`Found ${data.count} Wi-Fi networks`);

        } else if (data.type === 'ble_scan_result') {
            const devs = data.devices || [];
            setBleDevices(devs);

            if (trackedDevice && trackedDevice.type === 'ble') {
                const target = devs.find(d => d.address === trackedDevice.id);
                if (target) {
                    updateTrackedRSS(target.rssi);
                }
            }
            addLog(`Found ${data.count} BLE devices`);
        } else if (data.type === 'spectrum_result') {
            setSpectrum(data.data || []);
            addLog('Spectrum Scan Complete');
        } else if (data.type === 'csi') {
            setCsiData(prev => [...prev, data].slice(-50));
        } else if (data.type === 'sys_info') {
            setSysInfo(data);
            addLog(`System Info: ${data.chip}`);
        } else if (data.msg) {
            addLog(data.msg);
        }
    };

    const startTracking = (device, type) => {
        const id = type === 'wifi' ? (device.bssid || device.ssid) : device.address;
        const name = type === 'wifi' ? (device.ssid || 'Hidden') : (device.name || 'Unknown');
        setTrackedDevice({
            id,
            name,
            type,
            rssi: -100,
            history: new Array(20).fill(-100),
            lastSeen: new Date()
        });
        setActiveTab('dashboard');
        addLog(`TRACKING: ${name} (${id})`);
    };

    const stopTracking = () => {
        setTrackedDevice(null);
        addLog("Tracking Stopped");
    };

    const updateTrackedRSS = (rssi) => {
        setTrackedDevice(prev => {
            if (!prev) return null;
            const newHistory = [...prev.history, rssi].slice(-50);
            return {
                ...prev,
                rssi,
                history: newHistory,
                lastSeen: new Date()
            };
        });
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
                    <h1>CHIMERA<span style={{ color: '#fff' }}>RED</span> <span style={{ fontSize: '0.4em', color: '#666' }}>v1.1</span></h1>
                </div>
                <div className="controls">
                    {!connected ? (
                        <button className="btn" onClick={connectSerial}>Connect Device</button>
                    ) : (
                        <span className="badge" style={{ color: '#0f0', border: '1px solid #0f0' }}>CONNECTED</span>
                    )}
                </div>
            </header>

            <div className="toolbar glass" style={{ margin: '20px 20px 0 20px', padding: '10px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                <button className={`btn ${activeTab === 'dashboard' ? '' : 'btn-secondary'}`} onClick={() => setActiveTab('dashboard')}>Dashboard</button>
                <button className={`btn ${activeTab === 'wifi' ? '' : 'btn-secondary'}`} onClick={() => setActiveTab('wifi')}>Wi-Fi</button>
                <button className={`btn ${activeTab === 'ble' ? '' : 'btn-secondary'}`} onClick={() => setActiveTab('ble')}>BLE</button>
                <button className={`btn ${activeTab === 'csi' ? '' : 'btn-secondary'}`} onClick={() => setActiveTab('csi')}>CSI Radar</button>
                <button className={`btn ${activeTab === 'wigle' ? '' : 'btn-secondary'}`} onClick={() => setActiveTab('wigle')}>Wigle</button>
                <button className={`btn ${activeTab === 'terminal' ? '' : 'btn-secondary'}`} onClick={() => setActiveTab('terminal')}>Terminal</button>
            </div>

            <main>
                {activeTab === 'dashboard' && (
                    <Dashboard
                        trackedDevice={trackedDevice}
                        sysInfo={sysInfo}
                        connected={connected}
                        onStopTracking={stopTracking}
                        onSend={sendCommand}
                    />
                )}

                {activeTab === 'wifi' && (
                    <WiFiPanel
                        networks={networks}
                        onScan={() => sendCommand('SCAN_WIFI')}
                        onTrack={startTracking}
                    />
                )}

                {activeTab === 'ble' && (
                    <BLEPanel
                        devices={bleDevices}
                        onScan={() => sendCommand('SCAN_BLE')}
                        onTrack={startTracking}
                    />
                )}

                {activeTab === 'wigle' && (
                    <WiglePanel
                        gps={null}
                        onLog={addLog}
                        onTrack={startTracking}
                    />
                )}

                {activeTab === 'csi' && (
                    <CSIPanel
                        csiData={csiData}
                        onStart={() => sendCommand('START_CSI')}
                        onStop={() => sendCommand('STOP_CSI')}
                    />
                )}

                {activeTab === 'terminal' && (
                    <TerminalView logs={logs} onSend={sendCommand} />
                )}
            </main>
        </div>
    )
}

export default App
