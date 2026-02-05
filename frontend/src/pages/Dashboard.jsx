import React, { useEffect, useState } from 'react';
import { Activity, ShieldCheck, ShieldX, Settings } from 'lucide-react';
import api from '../api';
import analyticsWs from '../utils/websocket';

export default function Dashboard() {
    const [stats, setStats] = useState({
        totalPolicies: 0,
        requestsAllowed: 0,
        requestsBlocked: 0,
        loading: true,
        error: null,
        isConnected: false
    });

    useEffect(() => {
        // Initial load from REST API
        fetchInitialStats();
        
        // Subscribe to WebSocket updates
        const unsubscribe = analyticsWs.subscribe((update) => {
            setStats(prev => ({
                ...prev,
                requestsAllowed: update.requestsAllowed || 0,
                requestsBlocked: update.requestsBlocked || 0,
                totalPolicies: update.activePolicies || 0,
                loading: false,
                error: null,
                isConnected: true
            }));
        });
        
        return () => unsubscribe();
    }, []);

    const fetchInitialStats = async () => {
        try {
            const [policiesRes, analyticsRes] = await Promise.all([
                api.get('/admin/policies'),
                api.get('/analytics/summary')
            ]);

            setStats(prev => ({
                ...prev,
                totalPolicies: policiesRes.data.length,
                requestsAllowed: analyticsRes.data.allowed || 0,
                requestsBlocked: analyticsRes.data.blocked || 0,
                loading: false,
                error: null
            }));
        } catch (err) {
            console.error("Failed to fetch stats", err);
            setStats(prev => ({
                ...prev,
                loading: false,
                error: 'Failed to fetch statistics'
            }));
        }
    };

    if (stats.loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
            </div>
        );
    }

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Dashboard</h1>
                {stats.isConnected && (
                    <div className="flex items-center space-x-2 text-sm text-green-600 bg-green-50 px-3 py-1 rounded-full">
                        <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div>
                        <span>Real-time</span>
                    </div>
                )}
            </div>

            {stats.error && (
                <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-6">
                    {stats.error}
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
                <StatCard
                    icon={<ShieldCheck className="w-6 h-6" />}
                    label="Requests Allowed"
                    value={stats.requestsAllowed.toLocaleString()}
                    subtext="Total allowed requests"
                    color="green"
                />
                <StatCard
                    icon={<ShieldX className="w-6 h-6" />}
                    label="Requests Blocked"
                    value={stats.requestsBlocked.toLocaleString()}
                    subtext="Total blocked requests"
                    color="red"
                />
                <StatCard
                    icon={<Activity className="w-6 h-6" />}
                    label="Active Policies"
                    value={stats.totalPolicies}
                    subtext="Rate limiting policies"
                    color="indigo"
                />
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
                <h2 className="text-lg font-semibold mb-4">System Status</h2>
                <div className="flex items-center space-x-3">
                    <div className="w-3 h-3 rounded-full bg-green-500 animate-pulse"></div>
                    <span className="text-gray-700">Gateway is running and accepting requests</span>
                </div>
                <div className="mt-4 text-sm text-gray-500">
                    <p>Backend API: <code className="bg-gray-100 px-2 py-1 rounded">http://localhost:8080</code></p>
                    <p className="mt-1">Frontend: <code className="bg-gray-100 px-2 py-1 rounded">http://localhost:3000</code></p>
                </div>
            </div>
        </div>
    );
}

function StatCard({ icon, label, value, subtext, color }) {
    const colorClasses = {
        indigo: 'bg-indigo-50 text-indigo-600',
        green: 'bg-green-50 text-green-600',
        red: 'bg-red-50 text-red-600',
    };

    return (
        <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
            <div className="flex items-center justify-between">
                <div className={`p-3 rounded-lg ${colorClasses[color]}`}>
                    {icon}
                </div>
            </div>
            <p className="text-gray-500 text-sm font-medium mt-4">{label}</p>
            <p className="text-3xl font-bold mt-1">{value}</p>
            {subtext && <p className="text-xs text-gray-400 mt-1">{subtext}</p>}
        </div>
    );
}
