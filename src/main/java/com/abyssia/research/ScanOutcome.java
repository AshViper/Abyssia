package com.abyssia.research;

/** What a completed scan did: NEW = first registration of the target, FRAGMENT = a further discovery of a known target, KNOWN = nothing new. */
public enum ScanOutcome
{
    NEW, FRAGMENT, KNOWN
}
