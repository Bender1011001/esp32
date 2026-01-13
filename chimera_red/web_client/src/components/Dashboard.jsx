import React from 'react';
import { Activity, Zap, Target, X } from 'lucide-react';

export function Dashboard({ trackedDevice, sysInfo, connected, onStopTracking, onSend }) {
    return (
        <>
            {/* Tracking Overlay */}
            {trackedDevice && (
                <div className="card glass" style={{ borderColor: 'var(--primary)', boxShadow: '0 0 20px rgba(255, 0, 0, 0.2)' }}>
                    <div className="status-bar">
                        <h2 style={{ color: 'var(--primary)' }}><Target className="blink" size={24} /> TARGET LOCKED</h2>
                        <button className="btn btn-secondary" onClick={onStopTracking}><X size={16} /></button>
                    </div>
                    <div style={{ textAlign: 'center', margin: '20px 0' }}>
                        <h3 style={{ fontSize: '1.2em' }}>{trackedDevice.name}</h3>
                        <div style={{ fontSize: '0.8em', color: '#888', marginBottom: '10px' }}>{trackedDevice.id}</div>

                        <div style={{ fontSize: '3em', fontWeight: 'bold', color: trackedDevice.rssi > -60 ? '#0f0' : (trackedDevice.rssi > -80 ? '#fa0' : '#f00') }}>
                            {trackedDevice.rssi} <span style={{ fontSize: '0.4em', color: '#aaa' }}>dBm</span>
                        </div>
                        <div style={{ height: '10px', background: '#333', borderRadius: '5px', marginTop: '10px', overflow: 'hidden' }}>
                            <div style={{
                                height: '100%',
                                width: `${Math.min(100, Math.max(0, (trackedDevice.rssi + 100) * 2))}%`, // Map -100..-50 to 0..100%
                                background: trackedDevice.rssi > -60 ? '#0f0' : (trackedDevice.rssi > -80 ? '#fa0' : '#f00'),
                                transition: 'width 0.2s'
                            }} />
                        </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'flex-end', height: '60px', gap: '2px' }}>
                        {trackedDevice.history.map((val, i) => (
                            <div key={i} style={{
                                flex: 1,
                                background: 'var(--primary)',
                                opacity: 0.5,
                                height: `${Math.max(5, (val + 100) * 2)}%`,
                                borderRadius: '2px 2px 0 0'
                            }} />
                        ))}
                    </div>
                </div>
            )}

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
                <button className="btn" onClick={() => onSend('GET_INFO')}>Refresh Info</button>
            </div>

            <div className="card glass">
                <h2><Zap size={20} /> Quick Actions</h2>
                <div style={{ display: 'grid', gap: '10px', gridTemplateColumns: '1fr 1fr' }}>
                    <button className="btn" onClick={() => onSend('SCAN_WIFI')}>Scan Wi-Fi</button>
                    <button className="btn" onClick={() => onSend('SCAN_BLE')}>Scan BLE</button>
                </div>
            </div>
        </>
    );
}
