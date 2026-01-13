import React from 'react';
import { Wifi, Target } from 'lucide-react';

export function WiFiPanel({ networks, onScan, onTrack }) {
    return (
        <div className="card glass">
            <div className="status-bar">
                <h2><Wifi size={20} /> Wi-Fi Recon</h2>
                <button className="btn" onClick={onScan}>SCAN</button>
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
                            <button className="btn btn-secondary" style={{ padding: '5px' }} onClick={() => onTrack(net, 'wifi')}>
                                <Target size={16} />
                            </button>
                        </div>
                    </div>
                ))}
                {networks.length === 0 && <p style={{ padding: '20px', textAlign: 'center', color: '#555' }}>No networks found. Start a scan.</p>}
            </div>
        </div>
    );
}
