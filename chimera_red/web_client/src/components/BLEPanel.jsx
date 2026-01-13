import React from 'react';
import { Bluetooth, Target } from 'lucide-react';

export function BLEPanel({ devices, onScan, onTrack }) {
    return (
        <div className="card glass">
            <div className="status-bar">
                <h2><Bluetooth size={20} /> BLE Recon</h2>
                <button className="btn" onClick={onScan}>SCAN</button>
            </div>
            <div className="list-container">
                {devices.map((dev, i) => (
                    <div key={i} className="list-item">
                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                            <strong style={{ color: 'var(--secondary)' }}>{dev.name || 'Unknown Device'}</strong>
                            <span style={{ fontSize: '0.8em', color: '#888' }}>{dev.address}</span>
                        </div>
                        <span className="badge">{dev.rssi} dBm</span>
                        <button className="btn btn-secondary" style={{ padding: '5px', marginLeft: '10px' }} onClick={() => onTrack(dev, 'ble')}>
                            <Target size={16} />
                        </button>
                    </div>
                ))}
            </div>
        </div>
    );
}
